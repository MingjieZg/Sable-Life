package dev.sablelife.attack;

/** Authoritative core identity, independent of cosmetic names and entities. */
public interface CreatureAttackSource {
    java.util.UUID creatureOwner();
}
