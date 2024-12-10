/*
 * Copyright (c) 2010-2024. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.fab.trackmanager;

import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.spot.Detection;

import java.util.ArrayList;

public class TrackGroup {
    private final Sequence sequence;
    /**
     * DO NOT DIRECTLY ADD OR REMOVE tracks from this trackSegmentList. Always use getters and adder/setters
     */
    private final ArrayList<TrackSegment> trackSegmentList;

    @Contract(pure = true)
    public TrackGroup(final Sequence sequence) {
        this.sequence = sequence;
        trackSegmentList = new ArrayList<>();
    }

    /**
     * @return the sequence
     */
    public Sequence getSequence() {
        return sequence;
    }

    public ArrayList<TrackSegment> getTrackSegmentList() {
        return trackSegmentList;
    }

    public void addTrackSegment(final @NotNull TrackSegment ts) {
        if (ts.getOwnerTrackGroup() != null) {
            IcyLogger.warn(this.getClass(), "The trackSegment is already owned by another TrackGroup.");
            return;
        }

        //System.out.println("Track segment added to group. TS: " +ts.toString() );

        ts.setOwnerTrackGroup(this);
        trackSegmentList.add(ts);
    }

    public TrackSegment getTrackSegmentWithDetection(final Detection detection) {
        final ArrayList<TrackSegment> trackSegmentList = getTrackSegmentList();

        for (final TrackSegment ts : trackSegmentList) {
            if (ts.containsDetection(detection)) return ts;
        }

        return null;
    }

    public void clearAllTrackSegment() {
        final ArrayList<TrackSegment> trackSegmentListCopy = new ArrayList<>(trackSegmentList);

        for (final TrackSegment ts : trackSegmentListCopy) {
            removeTrackSegment(ts);
        }
    }

    public void removeTrackSegment(final @NotNull TrackSegment ts) {
        // should remove links
        ts.setOwnerTrackGroup(null);
        ts.removeAllLinks();
        trackSegmentList.remove(ts);
    }

    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
