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

import org.bioimageanalysis.icy.common.math.Random;
import plugins.nchenouard.spot.Detection;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TrackSegment is a pool of consecutive detection.
 *
 * @author Fabrice de Chaumont
 * @author Stephane
 */

public class TrackSegment implements Cloneable {
    // better to use weak reference in the map 
    protected final static Map<Integer, Reference<TrackSegment>> idKeyHashMapList = new HashMap<>(); // 1-1 hashmap.

    List<Detection> detectionList = new ArrayList<>();
    List<TrackSegment> previousList = new ArrayList<>();
    List<TrackSegment> nextList = new ArrayList<>();
    private TrackGroup ownerTrackGroup = null;
    private int id = 0;

    public static TrackSegment getTrackSegmentById(final int id) {
        final Integer iid = id;

        synchronized (idKeyHashMapList) {
            final Reference<TrackSegment> ref = idKeyHashMapList.get(iid);

            // null ? --> can remove it from the hashmap
            if (ref.get() == null) {
                idKeyHashMapList.remove(iid);
            }

            return ref.get();
        }
    }

    public TrackSegment() {
        super();

        generateId();
    }

    /**
     * Constructor with a list of detection
     */
    public TrackSegment(final ArrayList<Detection> detectionList) {
        super();

        this.detectionList = detectionList;
        generateId();

        // FIXME: add a duplicate owner test.
        // for (Detection detection : detectionList)
        // {
        // if (detection.getOwnerTrackSegment() != null)
        // {
        // System.err.println("TrackSegment : The detection is already owned by an other trackSegment.");
        // return;
        // }
        // detection.setOwnerTrackSegment(this);
        // }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        final TrackSegment cloneSegment = (TrackSegment) super.clone();

        for (final Detection detection : detectionList) {
            final Detection cloneDetection = (Detection) detection.clone();
            // cloneDetection.setOwnerTrackSegment(cloneSegment);
            cloneSegment.detectionList.add(cloneDetection);
        }

        previousList = new ArrayList<>(previousList);
        nextList = new ArrayList<>(nextList);

        generateId();

        return cloneSegment;

    }

    public void removeId() {
        synchronized (idKeyHashMapList) {
            idKeyHashMapList.remove(id);
        }
    }

    public int getId() {
        return id;
    }

    public void generateId() {
        // just for safety
        removeId();

        synchronized (idKeyHashMapList) {
            while (true) {
                final Integer key = Random.nextInt();

                // available ?
                if (idKeyHashMapList.get(key) == null) {
                    idKeyHashMapList.put(key, new WeakReference<>(this));
                    id = key;

                    return;
                }
            }
        }
    }

    public void setId(final int id) {
        final Integer key = id;

        synchronized (idKeyHashMapList) {
            // available ?
            if (idKeyHashMapList.get(key) == null) {
                idKeyHashMapList.put(key, new WeakReference<>(this));
                this.id = key;
            }
            else
                System.out.println("track id already loaded");
        }
    }

    /**
     *
     */
    public boolean containsDetection(final Detection detection) {
        return detectionList.contains(detection);
    }

    /**
     * set all dependent selection
     */
    public void setAllDetectionEnabled(final boolean selected) {
        for (final Detection d : detectionList)
            d.setEnabled(selected);
    }

    /**
     * is all dependent selection are enabled
     */
    public boolean isAllDetectionEnabled() {
        if (detectionList.isEmpty())
            return false;

        boolean result = true;
        for (final Detection d : detectionList)
            if (!d.isEnabled())
                result = false;

        return result;
    }

    /**
     * set all dependent selection
     */
    public void setAllDetectionSelected(final boolean selected) {
        for (final Detection d : detectionList)
            d.setSelected(selected);
    }

    /**
     * check if all dependent selection are selected
     */
    public boolean isAllDetectionSelected() {
        if (detectionList.isEmpty())
            return false;

        boolean result = true;
        for (final Detection d : detectionList)
            if (!d.isSelected())
                result = false;

        return result;
    }

    /**
     * Add a detection in the segmentTrack
     */
    public void addDetection(final Detection detection) {
        if (!detectionList.isEmpty()) {
            final Detection detectionPrevious = getLastDetection();
            if (detection.getT() != detectionPrevious.getT() + 1) {
                System.err.println(
                        "TrackSegment : The detection must be added with consecutive T value. Detection was not added");
                // throw new IllegalArgumentException();
                return;
            }

            // FIXME: check if the detection is already used by another plugin
            // if (detection.getOwnerTrackSegment() != null)
            // {
            // System.err.println("TrackSegment : The detection is already owned by an other trackSegment.");
            // return;
            // }
            // detection.setOwnerTrackSegment(this);
        }
        detectionList.add(detection);
    }

    public void removeDetection(final Detection detection) {
        // detection.setOwnerTrackSegment(null);
        detectionList.remove(detection);
    }

    /**
     * Remove a detection in the segmentTrack
     */
    public void removeLastDetection() {
        // getLastDetection().ownerTrackSegment = null;
        detectionList.remove(getLastDetection());
    }

    /**
     * Add a TrackSegment before this trackSegment
     */
    public void addPrevious(final TrackSegment trackSegment) {
        previousList.add(trackSegment);
        trackSegment.nextList.add(this);
    }

    /**
     * Remove a TrackSegment before this trackSegment
     */
    public void removePrevious(final TrackSegment trackSegment) {
        previousList.remove(trackSegment);
        trackSegment.nextList.remove(this);
    }

    /**
     * Add a TrackSegment after this trackSegment
     */
    public void addNext(final TrackSegment trackSegment) {
        nextList.add(trackSegment);
        trackSegment.previousList.add(this);
    }

    /**
     * Remove a TrackSegment after this trackSegment
     */
    public void removeNext(final TrackSegment trackSegment) {
        nextList.remove(trackSegment);
        trackSegment.previousList.remove(this);
    }

    /**
     * return first detection ( should be first in time too )
     */
    public Detection getFirstDetection() {
        if (detectionList.size() == 0)
            return null;

        return detectionList.get(0);
    }

    /**
     * return detection at index i
     */
    public Detection getDetectionAt(final int i) {
        return detectionList.get(i);
    }

    /**
     * return detection at time t
     */
    public Detection getDetectionAtTime(final int t) {
        for (final Detection detection : detectionList) {
            if (detection.getT() == t)
                return detection;
        }
        return null;
    }

    /**
     * return detection list
     * WARNING:
     * User should use addDetection and removeDetection instead of doing it himself using direct
     * access to the
     * ArrayList. Using addDetection or removeDetection ensure the property ownerTrackSegment of the
     * Detection
     * to be correctly updated.
     */
    public ArrayList<Detection> getDetectionList() {
        return (ArrayList<Detection>) detectionList;
    }

    /**
     * return last detection ( should be last in time too )
     */
    public Detection getLastDetection() {
        if (detectionList.isEmpty())
            return null;

        return detectionList.getLast();
    }

    /**
     * return detection index
     */
    public int getDetectionIndex(final Detection detection) {
        return detectionList.indexOf(detection);
    }

    public void setOwnerTrackGroup(final TrackGroup tg) {
        ownerTrackGroup = tg;
    }

    public TrackGroup getOwnerTrackGroup() {
        return ownerTrackGroup;
    }

    public void removeAllLinks() {
        final List<TrackSegment> previousListCopy = new ArrayList<>(previousList);
        for (final TrackSegment previousTrackSegment : previousListCopy)
            removePrevious(previousTrackSegment);

        final List<TrackSegment> nextListCopy = new ArrayList<>(nextList);
        for (final TrackSegment nextTrackSegment : nextListCopy)
            removeNext(nextTrackSegment);
    }
}
