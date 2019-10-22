package xyz.rthqks.synapse.data

@Suppress("LeakingThis")
sealed class Key<T>(
    val name: String
) {
    init {
        Key[name] = this
    }

    object AudioChannel : Key<Int>("audio_channel")
    object AudioEncoding : Key<Int>("audio_encoding")
    object AudioSampleRate : Key<Int>("audio_sample_rate")
    object AudioSource : Key<Int>("audio_source")

    companion object {
        private val map = mutableMapOf<String, Key<*>>()
        operator fun get(name: String) = map[name]
        private operator fun set(name: String, key: Key<*>) {
            map[name] = key
        }
    }
}