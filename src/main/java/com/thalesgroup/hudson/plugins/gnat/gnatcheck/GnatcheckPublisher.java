/*******************************************************************************
* Copyright (c) 2009 Thales Corporate Services SAS                             *
* Author : Gregory Boissinot                                                   *
*                                                                              *
* Permission is hereby granted, free of charge, to any person obtaining a copy *
* of this software and associated documentation files (the "Software"), to deal*
* in the Software without restriction, including without limitation the rights *
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell    *
* copies of the Software, and to permit persons to whom the Software is        *
* furnished to do so, subject to the following conditions:                     *
*                                                                              *
* The above copyright notice and this permission notice shall be included in   *
* all copies or substantial portions of the Software.                          *
*                                                                              *
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR   *
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,     *
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE  *
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER       *
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,*
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN    *
* THE SOFTWARE.                                                                *
*******************************************************************************/

package com.thalesgroup.hudson.plugins.gnat.gnatcheck;

import hudson.Launcher;
import hudson.Util;
import hudson.maven.AbstractMavenProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Result;
import hudson.tasks.Publisher;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import com.thalesgroup.hudson.plugins.gnat.GnatInstallation;
import com.thalesgroup.hudson.plugins.gnat.gnatmake.GnatmakeBuilder;
import com.thalesgroup.hudson.plugins.gnat.util.GnatException;
import com.thalesgroup.hudson.plugins.gnat.util.GnatUtil;

public class GnatcheckPublisher extends Publisher {

	public final static GnatcheckPublisherDescriptor DESCRIPTOR = new GnatcheckPublisherDescriptor();

	public final GnatcheckType[] types;
		
	
	public GnatcheckPublisher(GnatcheckType[] types){
		this.types= types;
	}	
	
	public Descriptor<Publisher> getDescriptor() {
		return DESCRIPTOR;
	}

	public static final class GnatcheckPublisherDescriptor extends
			Descriptor<Publisher> {


		public GnatcheckPublisherDescriptor() {
			super(GnatcheckPublisher.class);
			load();
		}

		@Override
		public String getDisplayName() {
			return "Run gnatcheck";
		}


        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)throws FormException {
            List<GnatcheckType> buildTypes = Descriptor.newInstancesFromHeteroList(
                    req, formData, "types", GnatcheckTypeDescriptor.LIST);
            return new GnatcheckPublisher(buildTypes.toArray(new GnatcheckType[buildTypes.size()]));
        }			
		
		@Override
		public String getHelpFile() {
			return "/plugin/gnat/gnatcheck/help.html";
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// Only for free-style projects
			return !AbstractMavenProject.class.isAssignableFrom(jobType);
		}

		public GnatInstallation[] getInstallations() {
			return GnatmakeBuilder.DESCRIPTOR.getInstallations();
		}

        public List<GnatcheckTypeDescriptor> getBuildTypes() {
            return GnatcheckTypeDescriptor.LIST; 
        }	 		
		
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}

	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean perform(Build<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		if (build.getResult().equals(Result.SUCCESS)
				|| (build.getResult().equals(Result.UNSTABLE))) {

			Project proj = build.getProject();
			
			for (GnatcheckType type:types){
				
				ArgumentListBuilder args = new ArgumentListBuilder();
				
				if (type instanceof ProjectGnatcheckType){
					
					ProjectGnatcheckType projectGnatcheckType = (ProjectGnatcheckType)type;
					
					String execPathGnat=null;
					try{
						execPathGnat=GnatUtil.getExecutable(projectGnatcheckType.getDescriptor().getInstallations(), type.gnatName, launcher,listener,GnatInstallation.GNAT_TYPE.GNAT);
					}
					catch (GnatException ge){
						ge.printStackTrace(listener.fatalError("error"));
						build.setResult(Result.FAILURE);
						return false;
					}	
					
					
					args.add(execPathGnat);
					args.add("check");
					args.add("-P");
					
					String normalizedProjectFile = projectGnatcheckType.projectFile.replaceAll("[\t\r\n]+", " ");								
					GnatUtil.addTokenIfExist(proj.getModuleRoot() + File.separator+ normalizedProjectFile,args, true, build);
					GnatUtil.addTokenIfExist(projectGnatcheckType.options,args, false, build);
					GnatUtil.addTokenIfExist(projectGnatcheckType.rule_options,args, false, build,"-rules");
				}
				else {
				
					FreeStyleGnatcheckType freeStyleGnatcheckType = (FreeStyleGnatcheckType)type;
					
					String execPathGnatcheck=null;
					try{
						execPathGnatcheck=GnatUtil.getExecutable(freeStyleGnatcheckType.getDescriptor().getInstallations(), type.gnatName, launcher,listener,GnatInstallation.GNAT_TYPE.GNATCHECK);					
					}
					catch (GnatException ge){
						ge.printStackTrace(listener.fatalError("error"));
						build.setResult(Result.FAILURE);
						return false;
					}
							
					args.add(execPathGnatcheck);
					GnatUtil.addTokenIfExist(freeStyleGnatcheckType.switches,args, false, build);
					GnatUtil.addTokenIfExist(freeStyleGnatcheckType.filename,args, true, build);
					GnatUtil.addTokenIfExist(freeStyleGnatcheckType.gcc_switches,args, false, build, "-cargs");
					GnatUtil.addTokenIfExist(freeStyleGnatcheckType.rule_options,args, false, build, "-rules");	
				}			
			

				try {
					int r = launcher.launch(args.toCommandArray(),
							build.getEnvVars(), listener.getLogger(),
							proj.getModuleRoot()).join();
					if (r != 0) {
						build.setResult(Result.FAILURE);
						return false;
					}
				} catch (IOException e) {
					Util.displayIOException(e, listener);
					e.printStackTrace(listener.fatalError("error"));
					build.setResult(Result.FAILURE);
					return false;
				}
			}
		}

		return true;
	}

}