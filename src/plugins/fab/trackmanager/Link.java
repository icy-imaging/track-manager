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

import plugins.nchenouard.spot.Detection;

public class Link
{

    TrackSegment start;
    TrackSegment end;

    public Link(TrackSegment start, TrackSegment end)
    {
        this.start = start;
        this.end = end;
    }

    public Detection getEndDetection()
    {
        return end.getFirstDetection();
    }

    public TrackSegment getEndSegment()
    {
        return end;
    }

    public Detection getStartDetection()
    {
        return start.getLastDetection();
    }

    public TrackSegment getStartSegment()
    {
        return start;
    }
    
    @Override
    public String toString() {
    	return "link start:" + getStartDetection() + " end:" + getEndDetection();
    }

}
