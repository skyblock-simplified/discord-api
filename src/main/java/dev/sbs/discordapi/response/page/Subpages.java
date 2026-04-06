package dev.sbs.discordapi.response.page;

import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

public interface Subpages<T> {

    @NotNull ConcurrentList<T> getPages();

}
