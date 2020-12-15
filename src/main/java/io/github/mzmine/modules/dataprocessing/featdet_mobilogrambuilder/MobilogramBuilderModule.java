package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.preferences.MZminePreferences;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;

public class MobilogramBuilderModule implements MZmineRunnableModule {

  @Nonnull
  @Override
  public String getDescription() {
    return "Builds mobilograms for each frame";
  }

  @Nonnull
  @Override
  public ExitCode runModule(@Nonnull MZmineProject project, @Nonnull ParameterSet parameters,
      @Nonnull Collection<Task> tasks) {

    int numberOfThreads = MZmineCore.getConfiguration().getPreferences()
        .getParameter(MZminePreferences.numOfThreads).getValue();

    RawDataFile[] files = parameters.getParameter(MobilogramBuilderParameters.rawDataFiles)
        .getValue().getMatchingRawDataFiles();

    for (RawDataFile file : files) {
      if (!(file instanceof IMSRawDataFile)) {
        continue;
      }

      // List<Frame> framesList = new ArrayList<>(((IMSRawDataFile) file).getFrames());
      // List<List<Frame>> frameLists =
      // Lists.partition(framesList, ((IMSRawDataFile) file).getFrames().size() / numberOfThreads);
      //
      // for (List<Frame> frames : frameLists) {
      // MobilogramBuilderTask task = new MobilogramBuilderTask(new HashSet<>(frames), parameters);
      // MZmineCore.getTaskController().addTask(task);
      // }

      Set<Frame> frames = new LinkedHashSet<>(((IMSRawDataFile) file).getFrames());
      MobilogramBuilderTask task =
          new MobilogramBuilderTask(project, file, new HashSet<>(frames), parameters);
      MZmineCore.getTaskController().addTask(task);
    }

    return ExitCode.OK;
  }

  @Nonnull
  @Override
  public MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.FEATUREDETECTION;
  }

  @Nonnull
  @Override
  public String getName() {
    return "Mobiligram builder";
  }

  @Nullable
  @Override
  public Class<? extends ParameterSet> getParameterSetClass() {
    return MobilogramBuilderParameters.class;
  }
}
