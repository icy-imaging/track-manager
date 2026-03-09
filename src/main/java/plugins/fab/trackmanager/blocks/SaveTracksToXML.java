/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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

package plugins.fab.trackmanager.blocks;

import org.bioimageanalysis.icy.common.collection.CollectionUtil;
import org.bioimageanalysis.icy.extension.plugin.abstract_.Plugin;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.jetbrains.annotations.NotNull;
import plugins.adufour.blocks.tools.roi.ROIBlock;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarMutable;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackManager;

import java.io.File;

/**
 * Block to save a set of ROI to a file
 *
 * @author Stéphane Dallongeville
 */
@IcyPluginName("Save Tracks To XML")
public class SaveTracksToXML extends Plugin implements ROIBlock {
    final Var<TrackGroup> tracks = new Var<>("Track group", new TrackGroup(null));
    final VarMutable file = new VarMutable("XML file", null) {
        @Override
        public boolean isAssignableFrom(final @NotNull Var source) {
            return (String.class == source.getType()) || (File.class == source.getType());
        }
    };

    @Override
    public void run() {
        final TrackGroup group = tracks.getValue();
        // nothing to do
        if (group == null)
            return;

        final Object obj = file.getValue();
        if (obj == null)
            return;

        final File f;

        if (obj instanceof String)
            f = new File((String) obj);
        else
            f = (File) obj;

        // save tracks into file
        TrackManager.saveTracks(f.getAbsolutePath(), CollectionUtil.createArrayList(group));
    }

    @Override
    public void declareInput(final @NotNull VarList inputMap) {
        inputMap.add("tracks", tracks);
        inputMap.add("file", file);
    }

    @Override
    public void declareOutput(final VarList outputMap) {
        //
    }
}
