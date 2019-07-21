package com.chairbender.mahjongeye;

import java.util.List;

/**
 * Simple POJO to communicate what happened after melding was done.
 */
public class MeldResult {
    /**
     * Melded boxes
     */
    public final List<Box> melds;

    /**
     * True if any melding was performed. False if no melding was performed.
     */
    public final boolean didMeld;

    public MeldResult(List<Box> melds, boolean melded) {
        this.melds = melds;
        this.didMeld = melded;
    }
}
