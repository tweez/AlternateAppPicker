package pk.qwerty12.alternateapppicker; 

import java.lang.reflect.Method;

import pk.qwerty12.alternateapppicker.R;
import android.content.Context;
import android.content.Intent;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.widget.AdapterView.OnItemClickListener;


/* This file contains portions of code licensed under the Apache License, Version 2.0, Copyright (C) 2008 The Android Open Source Project */
/* The alternate layout and implementation was originally - and nicely - done by Zaphod-Beeblebrox for the AOKP project, the hacky implementation and subsequent butchering is mine */ 

public class AlternateAppPicker implements IXposedHookZygoteInit {

	private static XModuleResources modRes;

	public void hacksToResolverActivity() {
		try {
			Class<?> classResolverActivity = XposedHelpers.findClass("com.android.internal.app.ResolverActivity", null);

			XposedHelpers.findAndHookMethod(classResolverActivity, "onCreate", Bundle.class, Intent.class, CharSequence.class, Intent[].class, java.util.List.class, boolean.class, 
					new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
							boolean problem = false;
							//By importing the resolver_grid_alt into a project built with the SDK, I had to remove two things: the string on the CheckBox and the layout direction, so bring them back in code
							/* First, set the correct string on the CheckBox.
							 * To do this within the layout, I'd have to import the strings from Android proper and include a local copy - no thanks. I did attempt to do the following using the Resource way but ended up with headaches, hence the parent stuff below
							 */
							final ViewGroup buttonLayout = (ViewGroup) ((View) XposedHelpers.getObjectField(param.thisObject, "mAlwaysButton")).getParent();
							if (buttonLayout instanceof ViewGroup) {
								//I can assume mAlwaysCheckBox will be at buttonLayout[0] (first child of the linearLayout), unless I change the layout, so skip iteration of buttonLayout with getChildCount & getChildAt
								final CheckBox mAlwaysCheckBox = (CheckBox) buttonLayout.getChildAt(0);
								if (mAlwaysCheckBox instanceof CheckBox) {
									//Set the text that we couldn't in the layout XML
									mAlwaysCheckBox.setText(modRes.getIdentifier("activity_resolver_use_always", "string", "android"));
									final GridView mGrid = (GridView) XposedHelpers.getObjectField(param.thisObject, "mGrid");
									/* Since my hook below causes a crash, just reimplement the listener here, which will work with the checkbox rather than relying upon the buttons, instead of trying to play nice with the original method. */
									mGrid.setOnItemClickListener(new OnItemClickListener() {
									      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
									          if (XposedHelpers.getBooleanField(param.thisObject, "mAlwaysUseOption")) {
									              final int checkedPos = mGrid.getCheckedItemPosition();
									              final boolean enabled = checkedPos != GridView.INVALID_POSITION;
									              if (enabled) {
									            	  XposedHelpers.callMethod(param.thisObject, "startSelected", position, mAlwaysCheckBox.isChecked());
									              }
									          } else {
									        	  XposedHelpers.callMethod(param.thisObject, "startSelected", position, false);
									          }
									      }
									});
								}
								else {
									problem = true;
								}
	
								//Next, set the layout direction that I had to remove from the layout itself since it was only publicly accessible from the next SDK version 
								Method setLayoutDirection = XposedHelpers.findMethodExact("android.view.View", null, "setLayoutDirection", int.class);
								setLayoutDirection.invoke(buttonLayout, 3); //LAYOUT_DIRECTION_LOCALE
							}
							else {
								problem = true;
							}
							
							if (problem) { //I don't know what the policy on informing users from a hack is, but maybe it will be appreciated
								Toast.makeText((Context) param.thisObject, "Something went wrong.\nPlease disable the " + AlternateAppPicker.class.getSimpleName() + " modification in Xposed Installer and let the developer know, please.", Toast.LENGTH_LONG).show();
							}
						}
				    });
			
			//Crashes
			/*XposedHelpers.findAndHookMethod(classResolverActivity, "onItemClick", AdapterView.class, View.class, int.class, long.class,
					new XC_MethodHook()
					{
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						}
				    });*/
			
		} catch (Throwable t) { XposedBridge.log(t); } 
	}

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		//Do the resource replacing part
		try {
			modRes = XModuleResources.createInstance(startupParam.modulePath, null);  
			XResources.setSystemWideReplacement("android", "layout", "resolver_grid", modRes.fwd(R.layout.resolver_grid_alt));
		} catch (Throwable t) { XposedBridge.log(t); }
		//Add the final touches to the layout and do the listening part
		hacksToResolverActivity();
	}

}