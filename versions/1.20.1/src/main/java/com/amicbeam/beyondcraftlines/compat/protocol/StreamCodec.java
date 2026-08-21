package com.amicbeam.beyondcraftlines.compat.protocol;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;

public interface StreamCodec<B extends ByteBuf, V> {
    V decode(B buffer);
    void encode(B buffer, V value);

    static <B extends ByteBuf, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Decoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override public V decode(B buffer) { return decoder.decode(buffer); }
            @Override public void encode(B buffer, V value) { encoder.encode(buffer, value); }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, V> unit(V value) {
        return of((buffer, ignored) -> {}, buffer -> value);
    }

    static <B extends ByteBuf, A, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga, Function<A, V> make) {
        return of((b, v) -> a.encode(b, ga.apply(v)), b -> make.apply(a.decode(b)));
    }

    static <B extends ByteBuf, A, C, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga,
            StreamCodec<? super B, C> c, Function<V, C> gc, Fn2<A, C, V> make) {
        return of((b, v) -> { a.encode(b, ga.apply(v)); c.encode(b, gc.apply(v)); },
                b -> make.apply(a.decode(b), c.decode(b)));
    }

    static <B extends ByteBuf, A, C, D, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga,
            StreamCodec<? super B, C> c, Function<V, C> gc,
            StreamCodec<? super B, D> d, Function<V, D> gd, Fn3<A, C, D, V> make) {
        return of((b, v) -> { a.encode(b, ga.apply(v)); c.encode(b, gc.apply(v)); d.encode(b, gd.apply(v)); },
                b -> make.apply(a.decode(b), c.decode(b), d.decode(b)));
    }

    static <B extends ByteBuf, A, C, D, E, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga,
            StreamCodec<? super B, C> c, Function<V, C> gc,
            StreamCodec<? super B, D> d, Function<V, D> gd,
            StreamCodec<? super B, E> e, Function<V, E> ge, Fn4<A, C, D, E, V> make) {
        return of((b, v) -> { a.encode(b, ga.apply(v)); c.encode(b, gc.apply(v)); d.encode(b, gd.apply(v)); e.encode(b, ge.apply(v)); },
                b -> make.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b)));
    }

    static <B extends ByteBuf, A, C, D, E, F, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga,
            StreamCodec<? super B, C> c, Function<V, C> gc,
            StreamCodec<? super B, D> d, Function<V, D> gd,
            StreamCodec<? super B, E> e, Function<V, E> ge,
            StreamCodec<? super B, F> f, Function<V, F> gf, Fn5<A, C, D, E, F, V> make) {
        return of((b, v) -> { a.encode(b, ga.apply(v)); c.encode(b, gc.apply(v)); d.encode(b, gd.apply(v)); e.encode(b, ge.apply(v)); f.encode(b, gf.apply(v)); },
                b -> make.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b), f.decode(b)));
    }

    static <B extends ByteBuf, A, C, D, E, F, G, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> a, Function<V, A> ga,
            StreamCodec<? super B, C> c, Function<V, C> gc,
            StreamCodec<? super B, D> d, Function<V, D> gd,
            StreamCodec<? super B, E> e, Function<V, E> ge,
            StreamCodec<? super B, F> f, Function<V, F> gf,
            StreamCodec<? super B, G> g, Function<V, G> gg, Fn6<A, C, D, E, F, G, V> make) {
        return of((b, v) -> { a.encode(b, ga.apply(v)); c.encode(b, gc.apply(v)); d.encode(b, gd.apply(v)); e.encode(b, ge.apply(v)); f.encode(b, gf.apply(v)); g.encode(b, gg.apply(v)); },
                b -> make.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b), f.decode(b), g.decode(b)));
    }

    interface Encoder<B, V> { void encode(B buffer, V value); }
    interface Decoder<B, V> { V decode(B buffer); }
    interface Fn2<A, B, V> { V apply(A a, B b); }
    interface Fn3<A, B, C, V> { V apply(A a, B b, C c); }
    interface Fn4<A, B, C, D, V> { V apply(A a, B b, C c, D d); }
    interface Fn5<A, B, C, D, E, V> { V apply(A a, B b, C c, D d, E e); }
    interface Fn6<A, B, C, D, E, F, V> { V apply(A a, B b, C c, D d, E e, F f); }
}
