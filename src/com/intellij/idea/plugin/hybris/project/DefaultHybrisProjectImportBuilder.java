package com.intellij.idea.plugin.hybris.project;

import com.intellij.idea.plugin.hybris.project.settings.DefaultHybrisImportParameters;
import com.intellij.idea.plugin.hybris.project.settings.HybrisImportParameters;
import com.intellij.idea.plugin.hybris.project.settings.HybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.project.tasks.SearchModulesRootsTaskModalWindow;
import com.intellij.idea.plugin.hybris.utils.HybrisI18NBundleUtils;
import com.intellij.idea.plugin.hybris.utils.HybrisIconsUtils;
import com.intellij.idea.plugin.hybris.utils.LibUtils;
import com.intellij.idea.plugin.hybris.utils.VirtualFileSystemUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.util.Function;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created 8:58 PM 07 June 2015
 *
 * @author Alexander Bartash <AlexanderBartash@gmail.com>
 */
public class DefaultHybrisProjectImportBuilder extends AbstractHybrisProjectImportBuilder {

    private static final Logger LOG = Logger.getInstance(DefaultHybrisProjectImportBuilder.class.getName());
    protected final ContentRootConfigurator contentRootConfigurator = new HybrisModuleContentRootConfigurator();
    protected final Lock lock = new ReentrantLock();
    @Nullable
    @GuardedBy("lock")
    protected volatile HybrisImportParameters projectImportParameters;

    @Override
    public void setRootProjectAbsolutePath(@NotNull final String path) {
        Validate.notEmpty(path);

        this.cleanup();

        ProgressManager.getInstance().run(new SearchModulesRootsTaskModalWindow(
            path, this.getProjectImportParameters()
        ));

        this.setFileToImport(path);
    }

    @Override
    public void cleanup() {
        super.cleanup();

        this.lock.lock();

        try {
            this.projectImportParameters = null;
        } finally {
            this.lock.unlock();
        }
    }

    @NotNull
    @Override
    public HybrisImportParameters getProjectImportParameters() {
        this.lock.lock();

        try {
            if (null == this.projectImportParameters) {
                this.projectImportParameters = new DefaultHybrisImportParameters(getCurrentProject());
            }

            return this.projectImportParameters;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean isOpenProjectSettingsAfter() {
        return this.getProjectImportParameters().isOpenProjectSettingsAfterImport();
    }

    @Override
    public void setOpenProjectSettingsAfter(final boolean on) {
        this.getProjectImportParameters().setOpenProjectSettingsAfterImport(on);
    }

    @Nullable
    @Override
    public List<Module> commit(final Project project,
                               final ModifiableModuleModel model,
                               final ModulesProvider modulesProvider,
                               final ModifiableArtifactModel artifactModel) {
        final boolean isProjectAlreadyOpen = null != model;
        final List<Module> result = new ArrayList<Module>();

        final List<HybrisModuleDescriptor> modulesChosenForImport = this.getProjectImportParameters()
                                                                        .getModulesChosenForImport();

        if (modulesChosenForImport.isEmpty()) {
            return Collections.emptyList();
        }

        this.performProjectsCleanup(modulesChosenForImport);

        final ModifiableModuleModel rootProjectModifiableModuleModel = (null != model)
            ? model
            : ModuleManager.getInstance(project).getModifiableModel();

        final Collection<ModifiableRootModel> modifiableRootModels = new ArrayList<ModifiableRootModel>();

        for (HybrisModuleDescriptor moduleDescriptor : modulesChosenForImport) {

            final Module javaModule = rootProjectModifiableModuleModel.newModule(
                moduleDescriptor.getModuleFile().getAbsolutePath(), StdModuleTypes.JAVA.getId()
            );

            final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(javaModule).getModifiableModel();

            modifiableRootModel.inheritSdk();

            final String libFolderPath = moduleDescriptor.getRootDirectory().getAbsolutePath() + File.separator
                                         + HybrisModuleContentRootConfigurator.LIB_DIRECTORY;
            LibUtils.loadLibFolder(project, libFolderPath);
            LibUtils.addProjectLibsToModule(project, modifiableRootModel);


            ClasspathStorage.setStorageType(modifiableRootModel, ClassPathStorageUtil.DEFAULT_STORAGE);
            this.contentRootConfigurator.configure(modifiableRootModel, moduleDescriptor);

            if (isProjectAlreadyOpen) {
                this.commitModule(modifiableRootModel);
            }

            result.add(javaModule);
            modifiableRootModels.add(modifiableRootModel);
        }

        if (!isProjectAlreadyOpen) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    ModifiableModelCommitter.multiCommit(modifiableRootModels, rootProjectModifiableModuleModel);
                }
            });
        }

        this.cleanup();

        return result;
    }

    protected void performProjectsCleanup(@NotNull final Iterable<HybrisModuleDescriptor> modulesChosenForImport) {
        Validate.notNull(modulesChosenForImport);

        final List<File> alreadyExistingModuleFiles = new ArrayList<File>();
        for (HybrisModuleDescriptor moduleDescriptor : modulesChosenForImport) {
            if (moduleDescriptor.getModuleFile().exists()) {
                alreadyExistingModuleFiles.add(moduleDescriptor.getModuleFile());
            }
        }

        Collections.sort(alreadyExistingModuleFiles);
        if (this.shouldRemoveAlreadyExistingModuleFiles(alreadyExistingModuleFiles)) {
            try {
                VirtualFileSystemUtils.removeAllFiles(alreadyExistingModuleFiles);
            } catch (IOException e) {
                LOG.error("Can not remove old module files.", e);
            }
        }
    }

    protected void commitModule(@NotNull final ModifiableRootModel modifiableRootModel) {
        Validate.notNull(modifiableRootModel);

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                modifiableRootModel.commit();
            }
        });
    }

    protected boolean shouldRemoveAlreadyExistingModuleFiles(@NotNull final Collection<File> files) {
        Validate.notNull(files);

        if (files.isEmpty()) {
            return false;
        }

        final int resultCode = Messages.showYesNoCancelDialog(
            HybrisI18NBundleUtils.message(
                "hybris.project.import.duplicate.modules.found",
                StringUtil.join(files, new GetFileNameFunction(), "\n")
            ),
            HybrisI18NBundleUtils.message("hybris.project.import.found.idea.module.files"),
            Messages.getQuestionIcon()
        );

        return (Messages.YES != resultCode) && (Messages.NO == resultCode);
    }

    @NotNull
    @Override
    public String getName() {
        return HybrisI18NBundleUtils.message("hybris.project.name");
    }

    @Override
    public Icon getIcon() {
        return HybrisIconsUtils.HYBRIS_ICON;
    }

    @Override
    public List<HybrisModuleDescriptor> getList() {
        return this.getProjectImportParameters().getFoundModules();
    }

    @Override
    public void setList(final List<HybrisModuleDescriptor> list) throws ConfigurationException {

        final Collection<HybrisModuleDescriptor> chosenForImport = new ArrayList<HybrisModuleDescriptor>(list);

        chosenForImport.removeAll(this.getProjectImportParameters().getAlreadyOpenedModules());

        this.getProjectImportParameters().getModulesChosenForImport().clear();
        this.getProjectImportParameters().getModulesChosenForImport().addAll(chosenForImport);
    }

    @Override
    public boolean isMarked(final HybrisModuleDescriptor element) {
        return false;
    }

    protected static class GetFileNameFunction implements Function<File, String> {

        @Override
        public String fun(final File param) {
            return param.getPath();
        }
    }
}