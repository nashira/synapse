package xyz.rthqks.synapse

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class PropertiesTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun properties() {
        val n = N(
            Foo(Key.AudioRate, 123),
            Foo(Key.Speed, 123f)
        )
        println(n)
        println(n.ps)

        val i: Int = n[Key.AudioRate]
        val f: Float = n[Key.Speed]

//        val k = Key[Key.Speed.name]
//        println(k)
    }
}

class N(vararg p: Pty<*>) {
    val ps = p.map {it.key to it}.toMap()

    operator fun <E> get(key: Key<E>): E {
        val p = ps[key]
        @Suppress("UNCHECKED_CAST")
        return p!!.value as E
    }

    operator fun <E> set(key: Key<E>, value: E) {
        @Suppress("UNCHECKED_CAST")
        val p = ps[key] as Pty<E>
        p.value = value
    }
}

abstract class Pty<E>(
    val key: Key<E>,
    var value: E
)

class Foo<E>(key: Key<E>, num: E): Pty<E>(key, num)

sealed class Key<E>(
    val name: String
) {
    init {
//        Key[name] = this
    }

    object AudioRate: Key<Int>("audio_rate")
    object Speed: Key<Float>("speed")

//    companion object {
//        private val nameMap = mutableMapOf<String, Pty<*>>()
//        private val keyMap = mutableMapOf<Key<*>, Pty<*>>()
//        operator fun get(name: String) = nameMap[name]
//        operator fun <E> set(name: String, property: Pty<E>) {
//            nameMap[name] = property
//        }
//        operator fun <E> get(key: Key<E>) = keyMap[key]
//        operator fun <E> set(key: Key<E>, property: Pty<E>) {
//            keyMap[key] = property
//        }
//    }
}
