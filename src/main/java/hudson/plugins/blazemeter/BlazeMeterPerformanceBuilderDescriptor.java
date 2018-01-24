/**
 * Copyright 2017 BlazeMeter Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hudson.plugins.blazemeter;

import com.blazemeter.api.explorer.Account;
import com.blazemeter.api.explorer.User;
import com.blazemeter.api.explorer.Workspace;
import com.blazemeter.api.explorer.test.AbstractTest;
import com.blazemeter.api.logging.Logger;
import com.blazemeter.api.logging.UserNotifier;
import com.blazemeter.api.utils.BlazeMeterUtils;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.plugins.blazemeter.utils.JenkinsBlazeMeterUtils;
import hudson.plugins.blazemeter.utils.Constants;
import hudson.plugins.blazemeter.utils.JenkinsTestListFlow;
import hudson.plugins.blazemeter.utils.Utils;
import hudson.plugins.blazemeter.utils.logger.BzmServerLogger;
import hudson.plugins.blazemeter.utils.notifier.BzmServerNotifier;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

@Symbol({"blazeMeterTest"})
@Extension
public class BlazeMeterPerformanceBuilderDescriptor extends BuildStepDescriptor<Builder> {

    private String NOT_FOUND = " is not found.";

    private String blazeMeterURL = Constants.A_BLAZEMETER_COM;
    private String NOT_DEFINED = "not defined";
    private String WORKSPACE_ID = "Workspace ID";
    private String name = "My BlazeMeter Account";
    public static String CHECK_SETTINGS_TESTS = "Check that workspace has tests and credentials/proxy are valid";
    public static String CHECK_CREDENTIALS = "Check that credentials/proxy are valid";

    private static BlazeMeterPerformanceBuilderDescriptor descriptor;

    public BlazeMeterPerformanceBuilderDescriptor() {
        super(PerformanceBuilder.class);
        this.load();
        BlazeMeterPerformanceBuilderDescriptor.descriptor = this;
    }

    public BlazeMeterPerformanceBuilderDescriptor(String blazeMeterURL) {
        super(PerformanceBuilder.class);
        this.load();
        this.blazeMeterURL = blazeMeterURL;
        BlazeMeterPerformanceBuilderDescriptor.descriptor = this;
    }

    public static BlazeMeterPerformanceBuilderDescriptor getDescriptor() {
        return BlazeMeterPerformanceBuilderDescriptor.descriptor;
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "BlazeMeter";
    }

    public ListBoxModel doFillTestIdItems(@QueryParameter("credentialsId") String crid,
                                          @QueryParameter("workspaceId") String wsid,
                                          @QueryParameter("testId") String savedTestId) {
        String resolvedTestId = Utils.resolveTestId(savedTestId);
        ListBoxModel items = new ListBoxModel();
        try {
            ProxyConfigurator.updateProxySettings(true);
            BlazemeterCredentialsBAImpl credentials = findCredentials(crid);
            if (credentials == null) {
                return items;
            }

            BlazeMeterUtils utils = getBzmUtils(credentials.getUsername(), credentials.getPassword().getPlainText());
            Workspace workspace = new Workspace(utils, wsid, NOT_DEFINED);
            items = testsList(workspace, resolvedTestId);
        } catch (Exception e) {
            items.clear();
        } finally {
            return items;
        }
    }

    public ListBoxModel doFillWorkspaceIdItems(@QueryParameter("credentialsId") String crid,
                                               @QueryParameter("workspaceId") String swid) {
        ListBoxModel items = new ListBoxModel();
        try {
            ProxyConfigurator.updateProxySettings(true);
            BlazemeterCredentialsBAImpl credentials = findCredentials(crid);
            if (credentials == null) {
                return items;
            }
            BlazeMeterUtils utils = getBzmUtils(credentials.getUsername(), credentials.getPassword().getPlainText());
            items = workspacesList(utils, swid);
        } catch (IOException e) {
            items.clear();
            items.add(new ListBoxModel.Option(e.getMessage(), e.getMessage(), true));
        } catch (Exception e) {
            items.clear();
            items.add(new ListBoxModel.Option(CHECK_CREDENTIALS, CHECK_CREDENTIALS, true));
        } finally {
            return items;
        }
    }

    public ListBoxModel doFillCredentialsIdItems(@QueryParameter("credentialsId") String credentialsId) {
        ListBoxModel items = new ListBoxModel();
        try {

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
            for (BlazemeterCredentials c : CredentialsProvider
                    .lookupCredentials(BlazemeterCredentialsBAImpl.class, item, ACL.SYSTEM)) {
                items.add(new ListBoxModel.Option(c.getDescription(),
                        c.getId(),
                        false));
            }
            setSelected(items, credentialsId, "Credentials ID");
        } catch (Exception npe) {

        } finally {
            return items;
        }
    }

    // Used by global.jelly to authenticate User key


    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
        String blazeMeterURL = formData.optString("blazeMeterURL");
        this.blazeMeterURL = blazeMeterURL.isEmpty() ? Constants.A_BLAZEMETER_COM : blazeMeterURL;
        this.save();
        return true;
    }

    public BlazemeterCredentialsBAImpl findCredentials(String credentialsId) {
        BlazemeterCredentialsBAImpl foundCredentials = null;
        for (BlazemeterCredentialsBAImpl c : CredentialsProvider
                .lookupCredentials(BlazemeterCredentialsBAImpl.class, Jenkins.getInstance(), ACL.SYSTEM)) {
            if (c.getId().equals(credentialsId)) {
                foundCredentials = c;
                break;
            }
        }
        return foundCredentials;
    }


    public static JenkinsBlazeMeterUtils getBzmUtils(String username, String password) {
        UserNotifier serverUserNotifier = new BzmServerNotifier();
        Logger logger = new BzmServerLogger();
        JenkinsBlazeMeterUtils utils = new JenkinsBlazeMeterUtils(username, password,
                BlazeMeterPerformanceBuilderDescriptor.descriptor.blazeMeterURL, serverUserNotifier, logger);

        return utils;
    }


    private ListBoxModel testsList(Workspace workspace, String savedTest) throws Exception {
        ListBoxModel sortedTests = new ListBoxModel();
        JenkinsTestListFlow jenkinsTestListFlow = new JenkinsTestListFlow(workspace.getUtils());
        List<AbstractTest> tests = jenkinsTestListFlow.getAllTestsForWorkspace(workspace);
        Comparator<AbstractTest> c = (AbstractTest t1, AbstractTest t2) -> t1.getName().compareToIgnoreCase(t2.getName());
        if (tests.isEmpty()) {
            sortedTests.clear();
            sortedTests.add(new ListBoxModel.Option(CHECK_SETTINGS_TESTS, CHECK_SETTINGS_TESTS, true));
            return sortedTests;
        }
        tests.sort(c);
        for (AbstractTest t : tests) {
            String testName = t.getName() + "(" + t.getId() + "." + t.getTestType() + ")";
            sortedTests.add(new ListBoxModel.Option(testName, t.getId() + "." + t.getTestType(), false));
        }
        setSelected(sortedTests, savedTest, "Test ID");
        return sortedTests;
    }

    private ListBoxModel setSelected(ListBoxModel box, String savedValue, String message) {
        int boxSize = box.size();
        boolean valueWasSelected = false;
        for (int i = 0; i < boxSize; i++) {
            ListBoxModel.Option option = box.get(i);
            if (option.value.contains(savedValue)) {
                box.get(i).selected = true;
                return box;
            }
        }
        if (!valueWasSelected & !savedValue.contains(CHECK_CREDENTIALS) & !savedValue.contains(CHECK_SETTINGS_TESTS)) {
            box.add(0, new ListBoxModel.Option(message + "(" + savedValue + ")" + NOT_FOUND,
                    savedValue));
            box.get(0).selected = true;
        }
        return box;
    }


    private ListBoxModel workspacesList(BlazeMeterUtils utils, String savedWorkspace) throws Exception {
        ListBoxModel workspacesList = new ListBoxModel();
        User user = User.getUser(utils);
        List<Account> accounts = user.getAccounts();
        for (Account a : accounts) {
            List<Workspace> workspaces = a.getWorkspaces();
            for (Workspace ws : workspaces) {
                ListBoxModel.Option wso = new ListBoxModel.Option(ws.getName() +
                        "(" + ws.getId() + ")", ws.getId(), false);
                workspacesList.add(wso);
            }
            setSelected(workspacesList, savedWorkspace, WORKSPACE_ID);
        }
        return workspacesList;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBlazeMeterURL() {
        return this.blazeMeterURL;
    }

    public void setBlazeMeterURL(String blazeMeterURL) {
        this.blazeMeterURL = blazeMeterURL;
    }

}

