package dev.sbs.discordapi.response.handler;

import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@RequiredArgsConstructor
public class FilterHandler<T> implements OutputHandler<Filter<T>> {

    private final @NotNull ConcurrentList<Filter<T>> items;
    @Setter private boolean cacheUpdateRequired;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FilterHandler<?> that = (FilterHandler<?>) o;

        return this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && Objects.equals(this.getItems(), that.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.isCacheUpdateRequired());
    }

}
