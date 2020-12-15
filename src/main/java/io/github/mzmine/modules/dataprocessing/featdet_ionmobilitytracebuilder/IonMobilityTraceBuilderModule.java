package io.github.mzmine.modules.dataprocessing.featdet_ionmobilitytracebuilder;

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
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;

public class IonMobilityTraceBuilderModule implements MZmineRunnableModule {

  @Nonnull
  @Override
  public String getDescription() {
    return "Builds ion mobility traces for a raw data file";
  }

  @Nonnull
  @Override
  public ExitCode runModule(@Nonnull MZmineProject project, @Nonnull ParameterSet parameters,
      @Nonnull Collection<Task> tasks) {

    RawDataFile[] files = parameters.getParameter(IonMobilityTraceBuilderParameters.rawDataFiles)
        .getValue().getMatchingRawDataFiles();

    for (RawDataFile file : files) {
      if (!(file instanceof IMSRawDataFile)) {
        continue;
      }

      Set<Frame> frames = new LinkedHashSet<>(((IMSRawDataFile) file).getFrames());
      IonMobilityTraceBuilderTask task =
          new IonMobilityTraceBuilderTask(project, file, new HashSet<>(frames), parameters);
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
    return "Ion mobility trace builder";
  }

  @Nullable
  @Override
  public Class<? extends ParameterSet> getParameterSetClass() {
    return IonMobilityTraceBuilderParameters.class;
  }
}
