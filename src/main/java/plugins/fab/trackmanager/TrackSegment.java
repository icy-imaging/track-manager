/*
 * Copyright 2010, 2011 Institut Pasteur.
 * 
 * This file is part of ICY.
 * 
 * ICY is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ICY is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ICY. If not, see <http://www.gnu.org/licenses/>.
 */
package plugins.fab.trackmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import icy.util.Random;
import plugins.nchenouard.spot.Detection;

/**
 * TrackSegment is a pool of consecutive detection.
 * 
 * @author Fabrice de Chaumont
 * @author Stephane
 */

public class TrackSegment implements Cloneable
{
    protected static Map<TrackSegment, Integer> idHashMapList = new HashMap<TrackSegment, Integer>(); // 1-1 hashmap.
    protected static Map<Integer, TrackSegment> idKeyHashMapList = new HashMap<Integer, TrackSegment>(); // 1-1 hashmap.

    List<Detection> detectionList = new ArrayList<Detection>();
    List<TrackSegment> previousList = new ArrayList<TrackSegment>();
    List<TrackSegment> nextList = new ArrayList<TrackSegment>();
    private TrackGroup ownerTrackGroup = null;
    private int id = 0;

    public static TrackSegment getTrackSegmentById(int id)
    {
        synchronized (idKeyHashMapList)
        {
            return idKeyHashMapList.get(Integer.valueOf(id));
        }
    }

    public TrackSegment()
    {
        super();

        generateId();
    }

    /** Constructor with a list of detection */
    public TrackSegment(ArrayList<Detection> detectionList)
    {
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
    protected Object clone() throws CloneNotSupportedException
    {
        TrackSegment cloneSegment = (TrackSegment) super.clone();

        for (Detection detection : detectionList)
        {
            Detection cloneDetection = (Detection) detection.clone();
            // cloneDetection.setOwnerTrackSegment(cloneSegment);
            cloneSegment.detectionList.add(cloneDetection);
        }

        previousList = new ArrayList<TrackSegment>(previousList);
        nextList = new ArrayList<TrackSegment>(nextList);

        generateId();

        return cloneSegment;

    }

    public void removeId()
    {
        synchronized (idKeyHashMapList)
        {
            synchronized (idHashMapList)
            {
                idKeyHashMapList.remove(Integer.valueOf(id));
                idHashMapList.remove(this);
            }
        }
    }

    public int getId()
    {
        return id;
    }

    public void generateId()
    {
        // just for safety
        removeId();

        synchronized (idKeyHashMapList)
        {
            synchronized (idHashMapList)
            {
                while (true)
                {
                    final Integer key = Integer.valueOf(Random.nextInt());

                    // available ?
                    if (idKeyHashMapList.get(key) == null)
                    {
                        idHashMapList.put(this, key);
                        idKeyHashMapList.put(key, this);

                        this.id = key.intValue();

                        return;
                    }
                }
            }
        }
    }

    public void setId(int id)
    {
        final Integer key = Integer.valueOf(id);

        synchronized (idKeyHashMapList)
        {
            synchronized (idHashMapList)
            {
                // available ?
                if (idKeyHashMapList.get(key) == null)
                {
                    idHashMapList.put(this, key);
                    idKeyHashMapList.put(key, this);

                    this.id = key.intValue();
                }
                else
                    System.out.println("track id already loaded");
            }
        }
    }

    /** */
    public boolean containsDetection(Detection detection)
    {
        return detectionList.contains(detection);
    }

    /** set all dependent selection */
    public void setAllDetectionEnabled(boolean selected)
    {
        for (Detection d : detectionList)
            d.setEnabled(selected);
    }

    /** is all dependent selection are enabled */
    public boolean isAllDetectionEnabled()
    {
        if (detectionList.isEmpty())
            return false;

        boolean result = true;
        for (Detection d : detectionList)
            if (!d.isEnabled())
                result = false;

        return result;
    }

    /** set all dependent selection */
    public void setAllDetectionSelected(boolean selected)
    {
        for (Detection d : detectionList)
            d.setSelected(selected);
    }

    /** check if all dependent selection are selected */
    public boolean isAllDetectionSelected()
    {
        if (detectionList.isEmpty())
            return false;

        boolean result = true;
        for (Detection d : detectionList)
            if (!d.isSelected())
                result = false;

        return result;
    }

    /** Add a detection in the segmentTrack */
    public void addDetection(Detection detection)
    {
        if (detectionList.size() > 0)
        {
            Detection detectionPrevious = getLastDetection();
            if (detection.getT() != detectionPrevious.getT() + 1)
            {
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

    public void removeDetection(Detection detection)
    {
        // detection.setOwnerTrackSegment(null);
        detectionList.remove(detection);
    }

    /** Remove a detection in the segmentTrack */
    public void removeLastDetection()
    {
        // getLastDetection().ownerTrackSegment = null;
        detectionList.remove(getLastDetection());
    }

    /** Add a TrackSegment before this trackSegment */
    public void addPrevious(TrackSegment trackSegment)
    {
        previousList.add(trackSegment);
        trackSegment.nextList.add(this);
    }

    /** Remove a TrackSegment before this trackSegment */
    public void removePrevious(TrackSegment trackSegment)
    {
        previousList.remove(trackSegment);
        trackSegment.nextList.remove(this);
    }

    /** Add a TrackSegment after this trackSegment */
    public void addNext(TrackSegment trackSegment)
    {
        nextList.add(trackSegment);
        trackSegment.previousList.add(this);
    }

    /** Remove a TrackSegment after this trackSegment */
    public void removeNext(TrackSegment trackSegment)
    {
        nextList.remove(trackSegment);
        trackSegment.previousList.remove(this);
    }

    /** return first detection ( should be first in time too ) */
    public Detection getFirstDetection()
    {
        if (detectionList.size() == 0)
            return null;

        return detectionList.get(0);
    }

    /** return detection at index i */
    public Detection getDetectionAt(int i)
    {
        return detectionList.get(i);
    }

    /** return detection at time t */
    public Detection getDetectionAtTime(int t)
    {
        for (Detection detection : detectionList)
        {
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
    public ArrayList<Detection> getDetectionList()
    {
        return (ArrayList<Detection>) detectionList;
    }

    /** return last detection ( should be last in time too ) */
    public Detection getLastDetection()
    {
        if (detectionList.size() == 0)
            return null;

        return detectionList.get(detectionList.size() - 1);
    }

    /** return detection index */
    public int getDetectionIndex(Detection detection)
    {
        return detectionList.indexOf(detection);
    }

    public void setOwnerTrackGroup(TrackGroup tg)
    {
        ownerTrackGroup = tg;
    }

    public TrackGroup getOwnerTrackGroup()
    {
        return ownerTrackGroup;
    }

    public void removeAllLinks()
    {
        List<TrackSegment> previousListCopy = new ArrayList<TrackSegment>(previousList);
        for (TrackSegment previousTrackSegment : previousListCopy)
            removePrevious(previousTrackSegment);

        List<TrackSegment> nextListCopy = new ArrayList<TrackSegment>(nextList);
        for (TrackSegment nextTrackSegment : nextListCopy)
            removeNext(nextTrackSegment);
    }
}
