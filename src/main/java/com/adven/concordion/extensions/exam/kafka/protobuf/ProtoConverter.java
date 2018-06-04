package com.adven.concordion.extensions.exam.kafka.protobuf;

import com.google.common.base.Optional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.protobuf.Descriptors.Descriptor;
import static com.google.protobuf.util.JsonFormat.TypeRegistry;

/**
 * @author Ruslan Ustits
 */
public abstract class ProtoConverter<F, T> {

    private final Set<Descriptor> descriptors;

    public ProtoConverter() {
        descriptors = new HashSet<>();
    }

    public abstract Optional<T> convert(final F from);

    public final void addDescriptor(final Descriptor descriptor) {
        descriptors.add(descriptor);
    }

    public final void addAllDescriptors(final List<Descriptor> descriptors) {
        this.descriptors.addAll(descriptors);
    }

    protected final TypeRegistry buildTypeRegistry() {
        final TypeRegistry.Builder builder = TypeRegistry.newBuilder();
        for (final Descriptor descriptor : descriptors) {
            builder.add(descriptor);
        }
        return builder.build();
    }

}
