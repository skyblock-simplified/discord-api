package dev.sbs.discordapi.response.handler;

import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
public class SortHandler<T> implements OutputHandler<Sorter<T>> {

    private final @NotNull ConcurrentList<Sorter<T>> items;
    private int currentSorterIndex = -1;
    private boolean reversed = false;
    @Setter private boolean cacheUpdateRequired;

    public SortHandler(@NotNull ConcurrentList<Sorter<T>> items) {
        this.items = items;
        this.gotoNext();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SortHandler<?> that = (SortHandler<?>) o;

        return this.getCurrentSorterIndex() == that.getCurrentSorterIndex()
            && this.isReversed() == that.isReversed()
            && this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && Objects.equals(this.getItems(), that.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.getCurrentSorterIndex(), this.isReversed(), this.isCacheUpdateRequired());
    }

    public @NotNull Optional<Sorter<T>> getCurrent() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getItems().get(this.getCurrentSorterIndex()) : null);
    }

    public void gotoNext() {
        if (this.notEmpty()) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= this.getItems().size())
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

}
