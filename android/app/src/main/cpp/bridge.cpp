#include <jni.h>
#include <mdr-c/Headphones.h>
#include <sstream>
#include <vector>
#include <memory>

// All engine calls and transport callbacks run on one Java worker thread.
struct Session {
    JNIEnv* env;
    jobject transport;
    jmethodID read, write, ready;
    MDRConnection connection{};
    MDRHeadphones* headphones = nullptr;
    int protocol = 1;
    bool needs_sync = false;
    ~Session() {
        if (headphones) mdrHeadphonesDestroy(headphones);
        env->DeleteGlobalRef(transport);
    }
};
static Session* session(jlong handle) { return reinterpret_cast<Session*>(handle); }
static bool exception(Session* s) {
    if (!s->env->ExceptionCheck()) return false;
    s->env->ExceptionClear();
    return true;
}
static MDRResult receive(void* user, char* dst, int size, int* received) {
    auto* s = static_cast<Session*>(user);
    auto bytes = s->env->NewByteArray(size);
    if (!bytes) return MDR_RESULT_ERROR_GENERAL;
    int count = s->env->CallIntMethod(s->transport, s->read, bytes);
    bool failed = exception(s);
    if (!failed && count > 0 && count <= size)
        s->env->GetByteArrayRegion(bytes, 0, count, reinterpret_cast<jbyte*>(dst));
    s->env->DeleteLocalRef(bytes);
    *received = count > 0 ? count : 0;
    return failed || count < 0 || count > size ? MDR_RESULT_ERROR_NET
         : count == 0 ? MDR_RESULT_INPROGRESS : MDR_RESULT_OK;
}
static MDRResult send(void* user, const char* src, int size, int* sent) {
    auto* s = static_cast<Session*>(user);
    auto bytes = s->env->NewByteArray(size);
    if (!bytes) return MDR_RESULT_ERROR_GENERAL;
    s->env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte*>(src));
    bool ok = s->env->CallBooleanMethod(s->transport, s->write, bytes);
    bool failed = exception(s);
    s->env->DeleteLocalRef(bytes);
    *sent = ok && !failed ? size : 0;
    return ok && !failed ? MDR_RESULT_OK : MDR_RESULT_ERROR_NET;
}
static MDRResult poll(void* user, int) {
    auto* s = static_cast<Session*>(user);
    int result = s->env->CallIntMethod(s->transport, s->ready);
    if (exception(s) || result < 0) return MDR_RESULT_ERROR_NET;
    return result ? MDR_RESULT_OK : MDR_RESULT_INPROGRESS;
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_soundconnect_android_NativeEngine_create(JNIEnv* env, jclass, jobject transport, jint protocol) {
    auto s = std::make_unique<Session>();
    s->env = env;
    s->protocol = protocol;
    s->transport = env->NewGlobalRef(transport);
    auto cls = env->GetObjectClass(transport);
    s->read = env->GetMethodID(cls, "read", "([B)I");
    s->write = env->GetMethodID(cls, "write", "([B)Z");
    s->ready = env->GetMethodID(cls, "ready", "()I");
    env->DeleteLocalRef(cls);
    s->connection.user = s.get();
    s->connection.recv = receive;
    s->connection.send = send;
    s->connection.poll = poll;
    s->connection.getLastError = [](void*) -> const char* { return "Android Bluetooth connection closed"; };
    if (mdrHeadphonesCreate(MDR_ABI_VERSION, &s->connection, protocol, &s->headphones) != MDR_RESULT_OK)
        return 0;
    if (mdrHeadphonesRequestInit(s->headphones) != MDR_RESULT_OK) return 0;
    return reinterpret_cast<jlong>(s.release());
}
extern "C" JNIEXPORT void JNICALL
Java_io_soundconnect_android_NativeEngine_destroy(JNIEnv*, jclass, jlong handle) { delete session(handle); }

extern "C" JNIEXPORT jint JNICALL
Java_io_soundconnect_android_NativeEngine_tick(JNIEnv*, jclass, jlong handle) {
    auto* s = session(handle);
    auto* h = s->headphones;
    for (int i = 0; i < 32; i++) {
        MDREvent event = MDR_EVENT_NONE;
        auto result = mdrHeadphonesPoll(h, &event);
        if (result != MDR_RESULT_OK && result != MDR_RESULT_INPROGRESS) return result;
        if (event == MDR_EVENT_INITIALIZE_COMPLETE || event == MDR_EVENT_NEED_SYNC) s->needs_sync = true;
        if (s->needs_sync && mdrHeadphonesIsReady(h)) {
            result = mdrHeadphonesRequestSync(h);
            if (result != MDR_RESULT_OK && result != MDR_RESULT_INPROGRESS) return result;
            if (result == MDR_RESULT_OK) s->needs_sync = false;
        }
        if (event == MDR_EVENT_ALERT) return -1;
        if (event == MDR_EVENT_INTERACTION) return -2;
        if (event == MDR_EVENT_NONE) break;
    }
    return 0;
}
extern "C" JNIEXPORT jstring JNICALL
Java_io_soundconnect_android_NativeEngine_text(JNIEnv* env, jclass, jlong handle, jint kind, jint index) {
    uint32_t count = 0;
    auto* h = session(handle)->headphones;
    mdrHeadphonesGetText(h, kind, index, nullptr, &count);
    if (count == 0 || count > 65536) return env->NewStringUTF("");
    std::vector<char> text(count + 1, 0);
    if (mdrHeadphonesGetText(h, kind, index, text.data(), &count) != MDR_RESULT_OK)
        return env->NewStringUTF("");
    // Decode standard UTF-8 in Java, including non-BMP track/device names.
    auto bytes = env->NewByteArray(static_cast<jsize>(count ? count - 1 : 0));
    env->SetByteArrayRegion(bytes, 0, count ? count - 1 : 0, reinterpret_cast<jbyte*>(text.data()));
    auto cls = env->FindClass("java/lang/String");
    auto ctor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(cls, ctor, bytes, charset));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(charset); env->DeleteLocalRef(cls);
    return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_io_soundconnect_android_NativeEngine_snapshot(JNIEnv* env, jclass, jlong handle) {
    auto* h = session(handle)->headphones;
    std::ostringstream out;
    out << "{\"ready\":" << mdrHeadphonesIsReady(h) << ",\"features\":[";
    for (uint32_t i = 1; i <= MDR_FEATURE_MAX_VALUE; i++) {
        MDRFeatureAvailability availability = MDR_AVAILABILITY_UNKNOWN;
        mdrHeadphonesGetFeature(h, i, &availability);
        if (i > 1) out << ',';
        out << (availability == MDR_AVAILABILITY_AVAILABLE ? 1 : 0);
    }
    out << "],\"battery\":[";
    MDRBattery batteries[3]{}; uint32_t count = 3;
    if (mdrHeadphonesGetBatteries(h, batteries, &count) != MDR_RESULT_OK) count = 0;
    for (uint32_t i = 0; i < count; i++) {
        if (i) out << ',';
        out << "[" << batteries[i].part << ',' << batteries[i].present << ','
            << unsigned(batteries[i].level_percent) << ',' << batteries[i].charging << ']';
    }
    MDRNoiseControl noise{}; mdrHeadphonesGetNoiseControl(h, &noise);
    MDRSpeakToChat speech{}; mdrHeadphonesGetSpeakToChat(h, &speech);
    MDREqualizer eq{}; mdrHeadphonesGetEqualizer(h, &eq);
    MDRPower power{}; mdrHeadphonesGetPower(h, &power);
    MDRVoiceGuidance voice{}; mdrHeadphonesGetVoiceGuidance(h, &voice);
    MDRPlayback playback{}; mdrHeadphonesGetPlayback(h, &playback);
    MDRConnectionMode connection{}; mdrHeadphonesGetConnectionMode(h, &connection);
    out << "],\"noise\":" << noise.mode << ",\"ambient\":" << unsigned(noise.ambient_level)
        << ",\"focus\":" << noise.focus_on_voice << ",\"speech\":" << speech.enabled
        << ",\"preset\":" << eq.preset << ",\"bass\":" << int(eq.clear_bass)
        << ",\"dsee\":" << eq.dsee_enabled << ",\"eqAvailable\":" << eq.available
        << ",\"dseeAvailable\":" << eq.dsee_available
        << ",\"autoPause\":" << power.auto_pause << ",\"autoOff\":" << power.auto_power_off_minutes
        << ",\"voice\":" << voice.enabled << ",\"volume\":" << unsigned(playback.volume)
        << ",\"priority\":" << connection.audio_priority << ",\"bands\":[";
    int8_t bands[32]{}; count = 32;
    if (mdrHeadphonesGetEqualizerBands(h, bands, &count) != MDR_RESULT_OK) count = 0;
    for (uint32_t i = 0; i < count; i++) { if(i) out << ','; out << int(bands[i]); }
    out << "],\"presets\":[";
    MDREqualizerPreset presets[64]{}; count = 64;
    if (mdrHeadphonesGetEqualizerPresets(h, presets, &count) != MDR_RESULT_OK) count = 0;
    for (uint32_t i = 0; i < count; i++) { if(i) out << ','; out << presets[i]; }
    out << "]}";
    return env->NewStringUTF(out.str().c_str());
}
extern "C" JNIEXPORT jint JNICALL
Java_io_soundconnect_android_NativeEngine_command(JNIEnv* env, jclass, jlong handle, jint kind, jint value, jintArray values) {
    auto* h = session(handle)->headphones;
    if (kind == 15) return mdrHeadphonesRequestRespondToAlert(h,
        value ? MDR_ALERT_ACTION_POSITIVE : MDR_ALERT_ACTION_NEGATIVE);
    if (!mdrHeadphonesIsReady(h)) return MDR_RESULT_INPROGRESS;
    MDRResult result = MDR_RESULT_ERROR_INVALID_ARGUMENT;
    if (kind <= 2) {
        MDRNoiseControl n{}; result = mdrHeadphonesGetNoiseControl(h, &n);
        if (result != MDR_RESULT_OK) return result;
        if (kind == 0) {
            if(value < 0 || value > 2) return MDR_RESULT_ERROR_INVALID_ARGUMENT;
            n.mode = value;
            if (session(handle)->protocol == 1 && value == 1) n.ambient_level = 255;
            if (value == 2 && (n.ambient_level < 1 || n.ambient_level > 20)) n.ambient_level = 1;
        }
        if (kind == 1) { if(value < 0 || value > 20) return MDR_RESULT_ERROR_INVALID_ARGUMENT;
            n.mode = MDR_NOISE_MODE_AMBIENT; n.ambient_level = value; n.changing_asm_level = MDR_FALSE; }
        if (kind == 2) n.focus_on_voice = value != 0;
        result = mdrHeadphonesSetNoiseControl(h, &n);
    } else if (kind == 3) {
        MDRSpeakToChat s{}; result = mdrHeadphonesGetSpeakToChat(h, &s);
        if (result != MDR_RESULT_OK) return result;
        s.enabled = value != 0; result = mdrHeadphonesSetSpeakToChat(h, &s);
    } else if (kind >= 4 && kind <= 7) {
        MDREqualizer eq{}; result = mdrHeadphonesGetEqualizer(h, &eq);
        if (result != MDR_RESULT_OK) return result;
        if (kind == 4) eq.preset = value;
        if (kind == 5 || kind == 6) eq.preset = MDR_EQ_CUSTOM;
        if (kind == 5) { if(value < -10 || value > 10) return MDR_RESULT_ERROR_INVALID_ARGUMENT; eq.clear_bass = value; }
        if (kind == 7) eq.dsee_enabled = value != 0;
        result = mdrHeadphonesSetEqualizer(h, &eq);
        if (result == MDR_RESULT_OK && kind == 6 && values) {
            auto size = env->GetArrayLength(values);
            if (size != static_cast<int>(eq.band_count) || size > 32) return MDR_RESULT_ERROR_INVALID_ARGUMENT;
            jint input[32]; int8_t bands[32]; env->GetIntArrayRegion(values, 0, size, input);
            for (int i = 0; i < size; i++) {
                if(input[i] < -10 || input[i] > 10) return MDR_RESULT_ERROR_INVALID_ARGUMENT;
                bands[i] = input[i];
            }
            result = mdrHeadphonesSetEqualizerBands(h, bands, size);
        }
    } else if (kind == 8 || kind == 9 || kind == 13) {
        MDRPower p{}; result = mdrHeadphonesGetPower(h, &p);
        if (result != MDR_RESULT_OK) return result;
        if (kind == 8) p.auto_pause = value != 0;
        if (kind == 9) p.auto_power_off_minutes = value;
        if (kind == 13) p.shutdown_requested = MDR_TRUE;
        result = mdrHeadphonesSetPower(h, &p);
    } else if (kind == 10) {
        MDRVoiceGuidance v{}; result = mdrHeadphonesGetVoiceGuidance(h, &v);
        if (result != MDR_RESULT_OK) return result;
        v.enabled = value != 0; result = mdrHeadphonesSetVoiceGuidance(h, &v);
    } else if (kind == 11) {
        if(value < 0 || value > 30) return MDR_RESULT_ERROR_INVALID_ARGUMENT;
        MDRPlayback p{}; result = mdrHeadphonesGetPlayback(h, &p);
        if(result != MDR_RESULT_OK) return result;
        p.volume = value; result = mdrHeadphonesSetPlayback(h, &p);
    } else if (kind == 12) {
        MDRConnectionMode c{}; c.audio_priority = value;
        result = mdrHeadphonesSetConnectionMode(h, &c);
    } else if (kind == 14) {
        MDRPlaybackCommand c{}; c.action = value;
        result = mdrHeadphonesPlayback(h, &c);
    }
    if (result != MDR_RESULT_OK) return result;
    return mdrHeadphonesRequestCommit(h);
}
