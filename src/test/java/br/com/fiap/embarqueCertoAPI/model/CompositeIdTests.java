package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.AlertaId;
import br.com.fiap.embarqueCertoAPI.model.id.ConfirmacaoId;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeIdTests {

    static Stream<Class<?>> keyTypes() {
        return Stream.of(LinhaEstacaoId.class, ConfirmacaoId.class, AlertaId.class);
    }

    private Object key(Class<?> type, Long first, Long second) throws Exception {
        return type.getConstructor(Long.class, Long.class).newInstance(first, second);
    }

    @ParameterizedTest
    @MethodSource("keyTypes")
    void equalityAndHashUseBothScalarComponents(Class<?> type) throws Exception {
        Object a = key(type, 1L, 2L);
        Object b = key(type, 1L, 2L);
        Object c = key(type, 1L, 2L);
        assertThat(a).isEqualTo(a).isEqualTo(b);
        assertThat(b).isEqualTo(a).isEqualTo(c);
        assertThat(a).isEqualTo(c).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(null).isNotEqualTo(new Object());
        assertThat(a).isNotEqualTo(key(type, 9L, 2L)).isNotEqualTo(key(type, 1L, 9L));
        var keys = new HashSet<>();
        keys.add(a);
        keys.add(b);
        assertThat(keys).hasSize(1).contains(c);
    }

    @ParameterizedTest
    @MethodSource("keyTypes")
    void incompleteKeysHaveNullSafeEqualityAndHash(Class<?> type) throws Exception {
        Object empty = type.getConstructor().newInstance();
        assertThat(empty).isEqualTo(key(type, null, null)).hasSameHashCodeAs(key(type, null, null));
        assertThat(key(type, 1L, null)).isEqualTo(key(type, 1L, null)).isNotEqualTo(empty);
        assertThat(key(type, null, 2L)).isEqualTo(key(type, null, 2L)).isNotEqualTo(empty);
    }

    @ParameterizedTest
    @MethodSource("keyTypes")
    void serializationPreservesIdentity(Class<?> type) throws Exception {
        Object original = key(type, 123L, 456L);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(original).hasSameHashCodeAs(original);
        }
    }
}
