/*
 * Copyright (C) filoghost and contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.filoghost.holographicdisplays.placeholder.tracking;

import me.filoghost.holographicdisplays.placeholder.PlaceholderException;
import me.filoghost.holographicdisplays.placeholder.StandardPlaceholder;
import me.filoghost.holographicdisplays.placeholder.TickClock;
import me.filoghost.holographicdisplays.placeholder.parsing.PlaceholderOccurrence;
import me.filoghost.holographicdisplays.placeholder.parsing.StringWithPlaceholders;
import me.filoghost.holographicdisplays.placeholder.registry.PlaceholderExpansion;
import me.filoghost.holographicdisplays.placeholder.registry.PlaceholderRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.WeakHashMap;

public class PlaceholderTracker {

    private final PlaceholderRegistry registry;
    private final TickClock tickClock;
    private final PlaceholderExceptionHandler exceptionHandler;

    // Use WeakHashMap to ensure that when a PlaceholderOccurrence is no longer referenced in other objects
    // the corresponding entry is removed from the map automatically.
    private final WeakHashMap<PlaceholderOccurrence, TrackedPlaceholder> activePlaceholders;

    public PlaceholderTracker(PlaceholderRegistry registry, TickClock tickClock) {
        this.registry = registry;
        this.tickClock = tickClock;
        this.exceptionHandler = new PlaceholderExceptionHandler(tickClock);
        this.activePlaceholders = new WeakHashMap<>();

        registry.setChangeListener(this::onRegistryChange);
    }

    private void onRegistryChange() {
        // Remove entries whose placeholder expansion sources are outdated
        activePlaceholders.entrySet().removeIf(entry -> {
            PlaceholderOccurrence placeholderOccurrence = entry.getKey();
            PlaceholderExpansion currentSource = entry.getValue().getSource();
            PlaceholderExpansion newSource = registry.find(placeholderOccurrence);

            return !Objects.equals(currentSource, newSource);
        });
    }

    public @Nullable String updateAndGetGlobalReplacement(PlaceholderOccurrence placeholderOccurrence) {
        return updateAndGetReplacement(placeholderOccurrence, null, false);
    }

    public @Nullable String updateAndGetIndividualReplacement(PlaceholderOccurrence placeholderOccurrence, Player player) {
        return updateAndGetReplacement(placeholderOccurrence, player, true);
    }
    
    private @Nullable String updateAndGetReplacement(PlaceholderOccurrence placeholderOccurrence, Player player, boolean individual) {
        try {
            TrackedPlaceholder trackedPlaceholder = getTrackedPlaceholder(placeholderOccurrence);
            if (trackedPlaceholder.isIndividual() == individual) {
                return trackedPlaceholder.updateAndGetReplacement(player, tickClock.getCurrentTick());
            } else {
                return null;
            }
        } catch (PlaceholderException e) {
            exceptionHandler.handle(e);
            return "[Error]";
        }
    }

    private @NotNull TrackedPlaceholder getTrackedPlaceholder(PlaceholderOccurrence placeholderOccurrence) throws PlaceholderException {
        TrackedPlaceholder trackedPlaceholder = activePlaceholders.get(placeholderOccurrence);

        if (trackedPlaceholder == null) {
            trackedPlaceholder = createTrackedPlaceholder(placeholderOccurrence);
            activePlaceholders.put(placeholderOccurrence, trackedPlaceholder);
        }

        return trackedPlaceholder;
    }

    private TrackedPlaceholder createTrackedPlaceholder(PlaceholderOccurrence placeholderOccurrence) throws PlaceholderException {
        PlaceholderExpansion placeholderExpansion = registry.find(placeholderOccurrence);
        StandardPlaceholder placeholder;
        
        if (placeholderExpansion != null) {
            placeholder = placeholderExpansion.createPlaceholder(placeholderOccurrence.getArgument());
        } else {
            placeholder = null;
        }
        
        if (placeholder == null) {
            return new TrackedNullPlaceholder(placeholderExpansion);
        } else if (placeholder.isIndividual()) {
            return new TrackedIndividualPlaceholder(placeholder, placeholderOccurrence);
        } else {
            return new TrackedGlobalPlaceholder(placeholder, placeholderOccurrence);
        }
    }

    public boolean containsIndividualPlaceholders(StringWithPlaceholders nameWithPlaceholders) {
        return nameWithPlaceholders.anyMatch(occurrence -> {
            PlaceholderExpansion placeholderExpansion = registry.find(occurrence);
            return placeholderExpansion != null && placeholderExpansion.isIndividual();
        });
    }


}
