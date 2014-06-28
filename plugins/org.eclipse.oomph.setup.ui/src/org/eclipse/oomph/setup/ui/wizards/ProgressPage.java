/*
 * Copyright (c) 2014 Eike Stepper (Berlin, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.oomph.setup.ui.wizards;

import org.eclipse.oomph.internal.setup.core.SetupTaskPerformer;
import org.eclipse.oomph.internal.setup.core.util.EMFUtil;
import org.eclipse.oomph.setup.Installation;
import org.eclipse.oomph.setup.SetupTask;
import org.eclipse.oomph.setup.Trigger;
import org.eclipse.oomph.setup.User;
import org.eclipse.oomph.setup.Workspace;
import org.eclipse.oomph.setup.log.ProgressLog;
import org.eclipse.oomph.setup.log.ProgressLogFilter;
import org.eclipse.oomph.setup.log.ProgressLogProvider;
import org.eclipse.oomph.setup.log.ProgressLogRunnable;
import org.eclipse.oomph.setup.ui.SetupUIPlugin;
import org.eclipse.oomph.setup.ui.ToolTipLabelProvider;
import org.eclipse.oomph.setup.util.FileUtil;
import org.eclipse.oomph.setup.util.OS;
import org.eclipse.oomph.ui.ErrorDialog;
import org.eclipse.oomph.ui.UIUtil;
import org.eclipse.oomph.util.IORuntimeException;
import org.eclipse.oomph.util.ReflectUtil;

import org.eclipse.emf.common.ui.viewer.ColumnViewerInformationControlToolTipSupport;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.provider.IItemFontProvider;
import org.eclipse.emf.edit.provider.ItemProvider;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider;
import org.eclipse.emf.edit.ui.provider.ExtendedFontRegistry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.internal.progress.ProgressManager;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eike Stepper
 */
public class ProgressPage extends SetupWizardPage
{
  public static final String PROP_SETUP_CONFIRM_SKIP = "oomph.setup.confirm.skip";

  public static final String PROP_SETUP_OFFLINE_STARTUP = "oomph.setup.offline.startup";

  public static final String PROP_SETUP_MIRRORS_STARTUP = "oomph.setup.mirrors.startup";

  private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

  private final Map<SetupTask, Point> setupTaskSelections = new HashMap<SetupTask, Point>();

  private TreeViewer treeViewer;

  private final ISelectionChangedListener treeViewerSelectionChangedListener = new ISelectionChangedListener()
  {
    public void selectionChanged(SelectionChangedEvent event)
    {
      IStructuredSelection selection = (IStructuredSelection)event.getSelection();
      Object element = selection.getFirstElement();
      if (element instanceof EObject)
      {
        for (EObject eObject = (EObject)element; eObject != null; eObject = eObject.eContainer())
        {
          if (eObject != currentTask && eObject instanceof SetupTask)
          {
            Point textSelection = setupTaskSelections.get(eObject);
            if (textSelection != null)
            {
              // Force the first line to be scrolled into view
              logText.setSelection(textSelection.x, textSelection.x);

              // Treat -1 so that at selects to the very end.
              int end = textSelection.y;
              if (end == -1)
              {
                end = logText.getCharCount();
              }

              // Determine the number of lines of text to be selected
              String selectedText = logText.getText(textSelection.x, end);
              int lineFeedCount = 0;
              int carriageReturnCount = 0;
              for (int i = 0, length = selectedText.length(); i < length; ++i)
              {
                char c = selectedText.charAt(i);
                if (c == '\n')
                {
                  ++lineFeedCount;
                }
                else if (c == '\r')
                {
                  ++carriageReturnCount;
                }
              }

              // If the number of visible lines is greater than the number of lines in the selection, invert the
              // selection range to scroll the top line into view.
              int visibleLineCount = logText.getClientArea().height / logText.getLineHeight();
              if (lineFeedCount > visibleLineCount || carriageReturnCount > visibleLineCount)
              {
                logText.setSelection(end, textSelection.x);
              }
              else
              {
                logText.setSelection(textSelection.x, end);
              }
            }
          }
        }
      }
    }
  };

  private StyledText logText;

  private final Document logDocument = new Document();

  private final ProgressLogFilter logFilter = new ProgressLogFilter();

  private SetupTask currentTask;

  private ProgressPageLog progressPageLog;

  private boolean scrollLock;

  private boolean dismissAutomatically;

  private boolean launchAutomatically;

  private Button scrollLockButton;

  private Button dismissButton;

  private Button launchButton;

  public ProgressPage()
  {
    super("ProgressPage");
    setTitle("Progress");
    setDescription("Wait for the setup to complete or press Back to cancel and make changes.");
  }

  @Override
  protected Control createUI(final Composite parent)
  {
    Composite mainComposite = new Composite(parent, SWT.NONE);

    GridLayout mainLayout = new GridLayout();
    mainLayout.marginHeight = 0;
    mainLayout.marginBottom = 5;
    mainComposite.setLayout(mainLayout);

    SashForm sashForm = new SashForm(mainComposite, SWT.VERTICAL);
    sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));

    treeViewer = new TreeViewer(sashForm, SWT.BORDER);
    Tree tree = treeViewer.getTree();

    ILabelProvider labelProvider = createLabelProvider();
    treeViewer.setLabelProvider(labelProvider);

    final AdapterFactoryContentProvider contentProvider = new AdapterFactoryContentProvider(getAdapterFactory());
    treeViewer.setContentProvider(contentProvider);
    treeViewer.addSelectionChangedListener(treeViewerSelectionChangedListener);

    new ColumnViewerInformationControlToolTipSupport(treeViewer, new LocationListener()
    {
      public void changing(LocationEvent event)
      {
      }

      public void changed(LocationEvent event)
      {
      }
    });

    tree.setLayoutData(new GridData(GridData.FILL_BOTH));
    tree.setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE));

    TextViewer logTextViewer = new TextViewer(sashForm, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.CANCEL | SWT.MULTI);
    logTextViewer.setDocument(logDocument);

    logText = logTextViewer.getTextWidget();
    logText.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
    logText.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
    logText.setEditable(false);
    logText.setLayoutData(new GridData(GridData.FILL_BOTH));
    logText.getVerticalBar().addSelectionListener(new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent event)
      {
        if (event.detail == SWT.DRAG && !scrollLock)
        {
          scrollLockButton.setSelection(true);
          scrollLock = true;
        }
      }
    });

    return mainComposite;
  }

  @Override
  protected void createCheckButtons()
  {
    scrollLockButton = addCheckButton("Scroll lock", "Keep the log from scrolling to the end when new messages are added", false, null);
    scrollLock = scrollLockButton.getSelection();
    scrollLockButton.addSelectionListener(new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent e)
      {
        scrollLock = scrollLockButton.getSelection();
      }
    });

    dismissButton = addCheckButton("Dismiss automatically", "Dismiss this wizard when all setup tasks have performed successfully", false,
        "dismissAutomatically");
    dismissAutomatically = dismissButton.getSelection();
    dismissButton.addSelectionListener(new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent e)
      {
        dismissAutomatically = dismissButton.getSelection();
      }
    });

    if (getTrigger() == Trigger.BOOTSTRAP)
    {
      launchButton = addCheckButton("Launch automatically", "Launch the installed product when all setup tasks have performed successfully", true,
          "launchAutomatically");
    }
    else
    {
      launchButton = addCheckButton("Restart if needed", "Restart the current product if the installation has been changed by setup tasks", false,
          "restartIfNeeded");
    }

    launchAutomatically = launchButton.getSelection();
    launchButton.addSelectionListener(new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent e)
      {
        launchAutomatically = launchButton.getSelection();
      }
    });
  }

  @Override
  public void enterPage(boolean forward)
  {
    if (forward)
    {
      progressPageLog = new ProgressPageLog();
      logDocument.set("");

      final SetupTaskPerformer performer = getPerformer();
      performer.setProgress(progressPageLog);

      File renamed = null;
      if (getTrigger() == Trigger.BOOTSTRAP)
      {
        File configurationLocation = performer.getProductConfigurationLocation();
        if (configurationLocation.exists())
        {
          try
          {
            // This must happen before the performer opens the logStream for the first logging!
            renamed = FileUtil.rename(configurationLocation);
          }
          catch (RuntimeException ex)
          {
            throw ex;
          }
          catch (Exception ex)
          {
            throw new IORuntimeException(ex);
          }
        }
      }

      treeViewer.setInput(new ItemProvider(performer.getNeededTasks()));

      String jobName = "Executing " + getTrigger().toString().toLowerCase() + " tasks";
      performer.log(jobName);

      if (renamed != null)
      {
        performer.log("Renamed existing configuration folder to " + renamed);
      }

      run(jobName, new ProgressLogRunnable()
      {
        public Set<String> run(ProgressLog log) throws Exception
        {
          ProgressManager oldProgressProvider = ProgressManager.getInstance();
          ProgressLogProvider newProgressLogProvider = new ProgressLogProvider(progressPageLog, oldProgressProvider);

          IJobManager jobManager = Job.getJobManager();
          jobManager.setProgressProvider(newProgressLogProvider);

          try
          {
            performer.perform();
            return performer.getRestartReasons();
          }
          finally
          {
            jobManager.setProgressProvider(oldProgressProvider);
          }
        }
      });
    }
  }

  @Override
  public void leavePage(boolean forward)
  {
    if (forward)
    {
      setPageComplete(false);
    }
    else
    {
      performCancel();
      setPageComplete(false);
      setCancelState(true);
    }
  }

  @Override
  public void performCancel()
  {
    if (progressPageLog != null)
    {
      progressPageLog.cancel();
      progressPageLog = null;
    }
  }

  private ILabelProvider createLabelProvider()
  {
    return new ToolTipLabelProvider(getAdapterFactory())
    {
      @Override
      public Font getFont(Object element)
      {
        if (element == currentTask)
        {
          return ExtendedFontRegistry.INSTANCE.getFont(treeViewer.getControl().getFont(), IItemFontProvider.BOLD_FONT);
        }

        return super.getFont(element);
      }
    };
  }

  private void run(final String jobName, final ProgressLogRunnable runnable)
  {
    try
    {
      // Remember and use the progressPageLog that is valid at this point in time.
      final ProgressPageLog progressLog = progressPageLog;

      Runnable jobRunnable = new Runnable()
      {
        public void run()
        {
          final Job job = new Job(jobName)
          {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
              final Trigger trigger = getTrigger();
              long start = System.currentTimeMillis();
              boolean success = false;
              Set<String> restartReasons = null;

              try
              {
                restartReasons = runnable.run(progressLog);

                SetupTaskPerformer performer = getPerformer();
                saveLocalFiles(performer);

                if (launchAutomatically && trigger == Trigger.BOOTSTRAP)
                {
                  launchProduct(performer);
                }

                success = true;
              }
              catch (OperationCanceledException ex)
              {
                // Do nothing
              }
              catch (Throwable ex)
              {
                SetupUIPlugin.INSTANCE.log(ex);
                progressLog.log(ex);
              }
              finally
              {
                long seconds = (System.currentTimeMillis() - start) / 1000;
                progressLog.log("Took " + seconds + " seconds.");

                final AtomicBoolean disableCancelButton = new AtomicBoolean(true);
                SetupWizard wizard = getWizard();

                if (!(restartReasons == null || restartReasons.isEmpty()) && SetupUIPlugin.SETUP_IDE)
                {
                  progressLog.log("A restart is needed for the following reasons:");
                  for (String reason : restartReasons)
                  {
                    progressLog.log("  - " + reason);
                  }

                  wizard.setFinishAction(new Runnable()
                  {
                    public void run()
                    {
                      SetupUIPlugin.restart(trigger);
                    }
                  });

                  if (success && launchAutomatically)
                  {
                    wizard.performFinish();
                    return Status.OK_STATUS;
                  }

                  progressLog.log("Press Finish to restart now or Cancel to restart later.");
                  disableCancelButton.set(false);
                }
                else
                {
                  if (success && dismissAutomatically)
                  {
                    wizard.setFinishAction(new Runnable()
                    {
                      public void run()
                      {
                        IWizardContainer container = getContainer();
                        if (container instanceof WizardDialog)
                        {
                          WizardDialog dialog = (WizardDialog)container;
                          dialog.close();
                        }
                      }
                    });

                    wizard.performFinish();
                    return Status.OK_STATUS;
                  }

                  progressLog.log("Press Finish to close the dialog.");
                }

                UIUtil.asyncExec(new Runnable()
                {
                  public void run()
                  {
                    progressLog.setFinished();
                    setPageComplete(true);

                    if (disableCancelButton.get())
                    {
                      setCancelState(false);
                    }
                  }
                });
              }

              return Status.OK_STATUS;
            }
          };

          UIUtil.asyncExec(new Runnable()
          {
            public void run()
            {
              job.schedule();
            }
          });
        }
      };

      UIUtil.asyncExec(jobRunnable);
    }
    catch (Throwable ex)
    {
      SetupUIPlugin.INSTANCE.log(ex);
      ErrorDialog.open(ex);
    }
  }

  private void launchProduct(SetupTaskPerformer performer) throws Exception
  {
    OS os = performer.getOS();
    if (os.isCurrent())
    {
      performer.log("Launching the installed product...");

      String eclipseDir = os.getEclipseDir();
      String eclipseExecutable = os.getEclipseExecutable();
      String eclipsePath = new File(performer.getInstallationLocation(), eclipseDir + "/" + eclipseExecutable).getAbsolutePath();

      List<String> command = new ArrayList<String>();
      command.add(eclipsePath);

      File ws = performer.getWorkspaceLocation();
      if (ws != null)
      {
        command.add("-data");
        command.add(ws.toString());
      }

      command.add("-vmargs");
      command.add("-D" + PROP_SETUP_CONFIRM_SKIP + "=true");
      command.add("-D" + PROP_SETUP_OFFLINE_STARTUP + "=" + performer.isOffline());
      command.add("-D" + PROP_SETUP_MIRRORS_STARTUP + "=" + performer.isMirrors());

      ProcessBuilder builder = new ProcessBuilder(command);
      builder.start();
    }
    else
    {
      performer.log("Launching the installed product is not possible for cross-platform installs. Skipping.");
    }
  }

  private void saveLocalFiles(SetupTaskPerformer performer)
  {
    Installation installation = getInstallation();
    Resource installationResource = installation.eResource();
    URI installationResourceURI = installationResource.getURI();
    installationResource.setURI(URI.createFileURI(new File(performer.getProductConfigurationLocation(), "org.eclipse.oomph.setup/installation.setup")
        .toString()));

    Workspace workspace = getWorkspace();
    Resource workspaceResource = null;
    URI workspaceResourceURI = null;
    if (workspace != null)
    {
      workspaceResource = workspace.eResource();
      workspaceResourceURI = workspaceResource.getURI();
      workspaceResource.setURI(URI.createFileURI(new File(performer.getWorkspaceLocation(), ".metadata/.plugins/org.eclipse.oomph.setup/workspace.setup")
          .toString()));
    }

    User performerUser = performer.getUser();
    User user = getUser();
    user.getAcceptedLicenses().addAll(performerUser.getAcceptedLicenses());
    user.setUnsignedPolicy(performerUser.getUnsignedPolicy());

    EMFUtil.saveEObject(installation);
    if (workspace != null)
    {
      EMFUtil.saveEObject(workspace);
    }

    EMFUtil.saveEObject(user);

    installationResource.setURI(installationResourceURI);
    if (workspaceResource != null)
    {
      workspaceResource.setURI(workspaceResourceURI);
    }
  }

  private void setCancelState(boolean enabled)
  {
    try
    {
      IWizardContainer container = getContainer();
      Method method = ReflectUtil.getMethod(container.getClass(), "getButton", int.class);
      method.setAccessible(true);
      Button cancelButton = (Button)method.invoke(container, IDialogConstants.CANCEL_ID);
      cancelButton.setEnabled(enabled);

      scrollLockButton.setEnabled(enabled);
      dismissButton.setEnabled(enabled);
      if (launchButton != null)
      {
        launchButton.setEnabled(enabled);
      }
    }
    catch (Throwable ex)
    {
      // Ignore
    }
  }

  /**
   * @author Eike Stepper
   */
  private class ProgressPageLog implements ProgressLog
  {
    private final StringBuilder queue = new StringBuilder();

    private boolean canceled;

    private boolean finished;

    public ProgressPageLog()
    {
    }

    public boolean isCanceled()
    {
      return canceled;
    }

    public void cancel()
    {
      canceled = true;
    }

    public void setFinished()
    {
      finished = true;
      task(null);
    }

    public void task(final SetupTask setupTask)
    {
      UIUtil.asyncExec(new Runnable()
      {
        public void run()
        {
          SetupTask previousCurrentTask = currentTask;
          currentTask = setupTask;

          int offset = 0;
          if (previousCurrentTask != null)
          {
            Point previousTextSelection = setupTaskSelections.get(previousCurrentTask);
            offset = logText.getCharCount();
            int start = previousTextSelection.x;
            setupTaskSelections.put(previousCurrentTask, new Point(start, offset));
            treeViewer.refresh(previousCurrentTask, true);
          }

          if (setupTask != null)
          {
            setupTaskSelections.put(setupTask, new Point(offset, -1));
            treeViewer.refresh(setupTask, true);
            treeViewer.removeSelectionChangedListener(treeViewerSelectionChangedListener);
            treeViewer.setSelection(new StructuredSelection(setupTask), true);
            treeViewer.addSelectionChangedListener(treeViewerSelectionChangedListener);
          }
        }
      });
    }

    public void log(String line)
    {
      log(line, true);
    }

    public void log(IStatus status)
    {
      String string = SetupUIPlugin.toString(status);
      log(string, false);
    }

    public void log(Throwable t)
    {
      String string = SetupUIPlugin.toString(t);
      log(string, false);
    }

    public void log(String line, boolean filter)
    {
      if (finished)
      {
        return;
      }

      if (isCanceled())
      {
        throw new OperationCanceledException();
      }

      if (filter)
      {
        line = logFilter.filter(line);
      }

      if (line == null)
      {
        return;
      }

      boolean wasEmpty = enqueue(new Date(), line);
      if (wasEmpty)
      {
        UIUtil.asyncExec(new Runnable()
        {
          public void run()
          {
            String text = dequeue();
            appendText(text);
          }
        });
      }
    }

    private synchronized boolean enqueue(Date date, String line)
    {
      boolean wasEmpty = queue.length() == 0;

      queue.append('[');
      queue.append(TIME.format(date));
      queue.append("] ");
      queue.append(line);
      queue.append('\n');

      return wasEmpty;
    }

    private synchronized String dequeue()
    {
      String result = queue.toString();
      queue.setLength(0);
      return result;
    }

    private void appendText(String string)
    {
      try
      {
        int length = logDocument.getLength();
        logDocument.replace(length, 0, string);

        if (!scrollLock)
        {
          int lineCount = logText.getLineCount();
          logText.setTopIndex(lineCount - 1);
        }
      }
      catch (Exception ex)
      {
        SetupUIPlugin.INSTANCE.log(ex);
      }
    }
  }
}
