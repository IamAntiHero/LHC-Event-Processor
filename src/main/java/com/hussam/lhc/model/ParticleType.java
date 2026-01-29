package com.hussam.lhc.model;

/**
 * Particle types detected by the LHC that we support for analysis.
 * <p>
 * Currently tracks electron, muon, and proton collisions.
 * Can be extended to support more particle types as needed.
 * </p>
 */
public enum ParticleType {
    ELECTRON,
    MUON,
    PROTON
}