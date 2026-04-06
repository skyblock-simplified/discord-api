package dev.sbs.discordapi.response.handler;

import dev.sbs.discordapi.component.interaction.TextInput;
import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class SearchHandler<T> implements OutputHandler<Search<T>> {

    private final @NotNull ConcurrentList<Search<T>> items;
    private @NotNull Optional<Search<T>> pending = Optional.empty();
    private boolean cacheUpdateRequired;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SearchHandler<?> that = (SearchHandler<?>) o;

        return this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && Objects.equals(this.getPending(), that.getPending())
            && Objects.equals(this.getItems(), that.getItems());
    }

    public void search(@NotNull TextInput textInput) {
        if (this.notEmpty()) {
            this.pending = this.getItems()
                .stream()
                .filter(search -> search.getTextInput().getIdentifier().equals(textInput.getIdentifier()))
                .findFirst();

            if (this.getPending().isPresent()) {
                this.getPending().ifPresent(search -> search.updateLastMatch(textInput));
                this.setCacheUpdateRequired();
            }
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.getPending(), this.isCacheUpdateRequired());
    }

    @Override
    public void setCacheUpdateRequired(boolean cacheUpdateRequired) {
        this.cacheUpdateRequired = cacheUpdateRequired;

        if (!cacheUpdateRequired)
            this.pending = Optional.empty();
    }

}
