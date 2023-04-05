package plugins.fab.trackmanager.blocks;

import java.io.File;
import java.util.List;

import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginBundled;
import icy.plugin.interface_.PluginLibrary;
import plugins.adufour.blocks.tools.roi.ROIBlock;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarInteger;
import plugins.adufour.vars.lang.VarMutable;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackManager;

/**
 * Block to load a set of ROI from a file
 *
 * @author Stephane
 */
public class LoadTracksFromXML extends Plugin implements ROIBlock, PluginLibrary, PluginBundled {
    final VarMutable file = new VarMutable("XML file", null) {
        @Override
        public boolean isAssignableFrom(Var source) {
            return (String.class == source.getType()) || (File.class == source.getType());
        }
    };
    final VarInteger groupIndex = new VarInteger("Group index", 0);
    final Var<TrackGroup> tracks = new Var<>("Track group", new TrackGroup(null));

    @Override
    public void run() {
        final Object obj = file.getValue();
        if (obj == null)
            return;

        final File f;

        if (obj instanceof String)
            f = new File((String) obj);
        else
            f = (File) obj;

        // load track groups from file
        final List<TrackGroup> groups = TrackManager.loadTracks(f.getAbsolutePath());
        // set result
        tracks.setValue(groups.get(groupIndex.getValue().intValue()));
    }

    @Override
    public void declareInput(VarList inputMap) {
        inputMap.add("file", file);
        inputMap.add("groupIndex", groupIndex);
    }

    @Override
    public void declareOutput(VarList outputMap) {
        outputMap.add("tracks", tracks);

    }

    @Override
    public String getMainPluginClassName() {
        return TrackManager.class.getName();
    }
}
