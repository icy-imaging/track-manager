package plugins.fab.trackmanager.blocks;

import java.io.File;

import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginBundled;
import icy.plugin.interface_.PluginLibrary;
import icy.type.collection.CollectionUtil;
import plugins.adufour.blocks.tools.roi.ROIBlock;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarMutable;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackManager;

/**
 * Block to save a set of ROI to a file
 * 
 * @author Stephane
 */
public class SaveTracksToXML extends Plugin implements ROIBlock, PluginLibrary, PluginBundled
{
    final Var<TrackGroup> tracks = new Var<TrackGroup>("Track group", new TrackGroup(null));
    final VarMutable file = new VarMutable("XML file", null)
    {
        @Override
        public boolean isAssignableFrom(@SuppressWarnings("rawtypes") Var source)
        {
            return (String.class == source.getType()) || (File.class == source.getType());
        }
    };

    @Override
    public void run()
    {
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
    public void declareInput(VarList inputMap)
    {
        inputMap.add("tracks", tracks);
        inputMap.add("file", file);
    }

    @Override
    public void declareOutput(VarList outputMap)
    {
        //
    }

    @Override
    public String getMainPluginClassName()
    {
        return TrackManager.class.getName();
    }
}
