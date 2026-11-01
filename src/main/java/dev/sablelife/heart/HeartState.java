package dev.sablelife.heart;

/** One-way lifecycle: neither reattachment nor loading a save may resurrect a dead core. */
public enum HeartState {
    DORMANT, ALIVE, DYING, DEAD
}

