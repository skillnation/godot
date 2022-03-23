/*************************************************************************/
/*  Godot.java                                                           */
/*************************************************************************/
/*                       This file is part of:                           */
/*                           GODOT ENGINE                                */
/*                      https://godotengine.org                          */
/*************************************************************************/
/* Copyright (c) 2007-2022 Juan Linietsky, Ariel Manzur.                 */
/* Copyright (c) 2014-2022 Godot Engine contributors (cf. AUTHORS.md).   */
/*                                                                       */
/* Permission is hereby granted, free of charge, to any person obtaining */
/* a copy of this software and associated documentation files (the       */
/* "Software"), to deal in the Software without restriction, including   */
/* without limitation the rights to use, copy, modify, merge, publish,   */
/* distribute, sublicense, and/or sell copies of the Software, and to    */
/* permit persons to whom the Software is furnished to do so, subject to */
/* the following conditions:                                             */
/*                                                                       */
/* The above copyright notice and this permission notice shall be        */
/* included in all copies or substantial portions of the Software.       */
/*                                                                       */
/* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,       */
/* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF    */
/* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.*/
/* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY  */
/* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,  */
/* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE     */
/* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                */
/*************************************************************************/

package org.godotengine.godot;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.WINDOW_SERVICE;

import org.godotengine.godot.input.GodotEditText;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.GodotPluginRegistry;
import org.godotengine.godot.utils.GodotNetUtils;
import org.godotengine.godot.utils.PermissionsUtil;
import org.godotengine.godot.xr.XRMode;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Messenger;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.opengles.GL10;

public class Godot extends FrameLayout implements SensorEventListener {
	static final int MAX_SINGLETONS = 64;
	private IStub mDownloaderClientStub;
	private ClipboardManager mClipboard;

	private XRMode xrMode = XRMode.REGULAR;
	private boolean use_32_bits = false;
	private boolean use_immersive = false;
	private boolean use_debug_opengl = false;
	private boolean translucent = false;
	private boolean mStatePaused;
	private boolean activityResumed;
	private int mState;

	// Used to dispatch events to the main thread.
	private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

	private GodotHost godotHost;
	private GodotPluginRegistry pluginRegistry;

	static private Intent mCurrentIntent;

	private final Activity activity;
	private String expansion_pack_path;

	public Godot(@NonNull Context context, Activity activity) {
		this(context, activity, null);
	}

	public Godot(@NonNull Context context, Activity activity, @Nullable String expansionPackPath) {
		super(context);
		this.activity = activity;
		this.godotHost = (GodotHost)activity;
		this.expansion_pack_path = expansionPackPath;
	}

	public void onNewIntent(Intent intent) {
		mCurrentIntent = intent;
	}

	static public Intent getCurrentIntent() {
		return mCurrentIntent;
	}

	static public class SingletonBase {
		protected void registerClass(String p_name, String[] p_methods) {
			GodotPlugin.nativeRegisterSingleton(p_name, this);

			Class clazz = getClass();
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				boolean found = false;

				for (String s : p_methods) {
					if (s.equals(method.getName())) {
						found = true;
						break;
					}
				}
				if (!found)
					continue;

				List<String> ptr = new ArrayList<String>();

				Class[] paramTypes = method.getParameterTypes();
				for (Class c : paramTypes) {
					ptr.add(c.getName());
				}

				String[] pt = new String[ptr.size()];
				ptr.toArray(pt);

				GodotPlugin.nativeRegisterMethod(p_name, method.getName(), method.getReturnType().getName(), pt);
			}

			Godot.singletons[Godot.singleton_count++] = this;
		}

		/**
		 * Invoked once during the Godot Android initialization process after creation of the
		 * {@link GodotView} view.
		 * <p>
		 * This method should be overridden by descendants of this class that would like to add
		 * their view/layout to the Godot view hierarchy.
		 *
		 * @return the view to be included; null if no views should be included.
		 */
		@Nullable
		protected View onMainCreateView(Activity activity) {
			return null;
		}

		protected void onMainActivityResult(int requestCode, int resultCode, Intent data) {
		}

		protected void onMainRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		}

		protected void onMainPause() {}
		protected void onMainResume() {}
		protected void onMainDestroy() {}
		protected boolean onMainBackPressed() { return false; }

		protected void onGLDrawFrame(GL10 gl) {}
		protected void onGLSurfaceChanged(GL10 gl, int width, int height) {} // singletons will always miss first onGLSurfaceChanged call
		// protected void onGLSurfaceCreated(GL10 gl, EGLConfig config) {} // singletons won't be ready until first GodotLib.step()

		public void registerMethods() {}
	}

	/*
	protected List<SingletonBase> singletons = new ArrayList<SingletonBase>();
	protected void instanceSingleton(SingletonBase s) {

		s.registerMethods();
		singletons.add(s);
	}
	*/

	private String[] command_line;
	private boolean use_apk_expansion;

	public GodotView mView;
	private boolean godot_initialized = false;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private Sensor mGravity;
	private Sensor mMagnetometer;
	private Sensor mGyroscope;

	public static GodotIO io;
	public static GodotNetUtils netUtils;

	static SingletonBase[] singletons = new SingletonBase[MAX_SINGLETONS];
	static int singleton_count = 0;

	public interface ResultCallback {
		void callback(int requestCode, int resultCode, Intent data);
	}
	public ResultCallback result_callback;

//	@Override
//	public void onAttach(Context context) {
//		super.onAttach(context);
//		if (getParentFragment() instanceof GodotHost) {
//			godotHost = (GodotHost)getParentFragment();
//		} else if (getActivity() instanceof GodotHost) {
//			godotHost = (GodotHost)getActivity();
//		}
//	}

	public Activity getActivity() {
		return activity;
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		godotHost = null;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		this.onCreate(null);
	}

	//	@CallSuper
//	@Override
//	public void onActivityResult(int requestCode, int resultCode, Intent data) {
//		super.onActivityResult(requestCode, resultCode, data);
//		if (result_callback != null) {
//			result_callback.callback(requestCode, resultCode, data);
//			result_callback = null;
//		}
//
//		for (int i = 0; i < singleton_count; i++) {
//			singletons[i].onMainActivityResult(requestCode, resultCode, data);
//		}
//		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
//			plugin.onMainActivityResult(requestCode, resultCode, data);
//		}
//	}
//
//	@CallSuper
//	@Override
//	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//		for (int i = 0; i < singleton_count; i++) {
//			singletons[i].onMainRequestPermissionsResult(requestCode, permissions, grantResults);
//		}
//		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
//			plugin.onMainRequestPermissionsResult(requestCode, permissions, grantResults);
//		}
//
//		for (int i = 0; i < permissions.length; i++) {
//			GodotLib.requestPermissionResult(permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
//		}
//	}

	/**
	 * Invoked on the render thread when the Godot setup is complete.
	 */
	@CallSuper
	protected void onGodotSetupCompleted() {
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			plugin.onGodotSetupCompleted();
		}

		if (godotHost != null) {
			godotHost.onGodotSetupCompleted();
		}
	}

	/**
	 * Invoked on the render thread when the Godot main loop has started.
	 */
	@CallSuper
	protected void onGodotMainLoopStarted() {
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			plugin.onGodotMainLoopStarted();
		}

		if (godotHost != null) {
			godotHost.onGodotMainLoopStarted();
		}
	}

	/**
	 * Used by the native code (java_godot_lib_jni.cpp) to complete initialization of the GLSurfaceView view and renderer.
	 */
	@Keep
	private void onVideoInit() {
		boolean use_gl3 = getGLESVersionCode() >= 0x00030000;

		this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		// GodotEditText layout
		GodotEditText edittext = new GodotEditText(activity);
		edittext.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
				(int)getResources().getDimension(R.dimen.text_edit_height)));
		// ...add to FrameLayout
		this.addView(edittext);

		mView = new GodotView(activity, this, xrMode, use_gl3, use_32_bits, use_debug_opengl, translucent);
		this.addView(mView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		edittext.setView(mView);
		io.setEdit(edittext);

		mView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
			Point fullSize = new Point();
			activity.getWindowManager().getDefaultDisplay().getSize(fullSize);
			Rect gameSize = new Rect();
			mView.getWindowVisibleDisplayFrame(gameSize);
			final int keyboardHeight = fullSize.y - gameSize.bottom;
			GodotLib.setVirtualKeyboardHeight(keyboardHeight);
		});

		final String[] current_command_line = command_line;
		mView.queueEvent(() -> {
			GodotLib.setup(current_command_line);

			// Must occur after GodotLib.setup has completed.
			for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
				plugin.onRegisterPluginWithGodotNative();
			}
			setKeepScreenOn("True".equals(GodotLib.getGlobal("display/window/energy_saving/keep_screen_on")));

			// The Godot Android plugins are setup on completion of GodotLib.setup
			mainThreadHandler.post(() -> {
				// Include the non-null views returned in the Godot view hierarchy.
				for (int i = 0; i < singleton_count; i++) {
					View view = singletons[i].onMainCreateView(activity);
					if (view != null) {
						this.addView(view);
					}
				}
			});
		});

		// Include the returned non-null views in the Godot view hierarchy.
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			View pluginView = plugin.onMainCreate(activity);
			if (pluginView != null) {
				if (plugin.shouldBeOnTop()) {
					this.addView(pluginView);
				} else {
					this.addView(pluginView, 0);
				}
			}
		}
	}

	public void setKeepScreenOn(final boolean p_enabled) {
		runOnUiThread(() -> {
			if (p_enabled) {
				activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} else {
				activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		});
	}

	/**
	 * Used by the native code (java_godot_wrapper.h) to vibrate the device.
	 * @param durationMs
	 */
	@SuppressLint("MissingPermission")
	@Keep
	private void vibrate(int durationMs) {
		if (durationMs > 0 && requestPermission("VIBRATE")) {
			Vibrator v = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
				} else {
					// deprecated in API 26
					v.vibrate(durationMs);
				}
			}
		}
	}

	public void restart() {
		if (godotHost != null) {
			godotHost.onGodotRestartRequested(this);
		}
	}

	public void alert(final String message, final String title) {
		runOnUiThread(() -> {
			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setMessage(message).setTitle(title);
			builder.setPositiveButton(
					"OK",
					(dialog, id) -> dialog.cancel());
			AlertDialog dialog = builder.create();
			dialog.show();
		});
	}

	public int getGLESVersionCode() {
		ActivityManager am = (ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
		ConfigurationInfo deviceInfo = am.getDeviceConfigurationInfo();
		return deviceInfo.reqGlEsVersion;
	}

	@CallSuper
	protected String[] getCommandLine() {
		String[] original = parseCommandLine();
		String[] updated;
		List<String> hostCommandLine = godotHost != null ? godotHost.getCommandLine() : null;
		if (hostCommandLine == null || hostCommandLine.isEmpty()) {
			updated = original;
		} else {
			updated = Arrays.copyOf(original, original.length + hostCommandLine.size());
			for (int i = 0; i < hostCommandLine.size(); i++) {
				updated[original.length + i] = hostCommandLine.get(i);
			}
		}
		return updated;
	}

	private String[] parseCommandLine() {
		InputStream is;
		try {
			is = activity.getAssets().open("_cl_");
			byte[] len = new byte[4];
			int r = is.read(len);
			if (r < 4) {
				return new String[0];
			}
			int argc = ((int)(len[3] & 0xFF) << 24) | ((int)(len[2] & 0xFF) << 16) | ((int)(len[1] & 0xFF) << 8) | ((int)(len[0] & 0xFF));
			String[] cmdline = new String[argc];

			for (int i = 0; i < argc; i++) {
				r = is.read(len);
				if (r < 4) {
					return new String[0];
				}
				int strlen = ((int)(len[3] & 0xFF) << 24) | ((int)(len[2] & 0xFF) << 16) | ((int)(len[1] & 0xFF) << 8) | ((int)(len[0] & 0xFF));
				if (strlen > 65535) {
					return new String[0];
				}
				byte[] arg = new byte[strlen];
				r = is.read(arg);
				if (r == strlen) {
					cmdline[i] = new String(arg, "UTF-8");
				}
			}
			return cmdline;
		} catch (Exception e) {
			e.printStackTrace();
			return new String[0];
		}
	}

	/**
	 * Used by the native code (java_godot_wrapper.h) to check whether the activity is resumed or paused.
	 */
	@Keep
	private boolean isActivityResumed() {
		return activityResumed;
	}

	/**
	 * Used by the native code (java_godot_wrapper.h) to access the Android surface.
	 */
	@Keep
	private Surface getSurface() {
		return mView.getHolder().getSurface();
	}

	/**
	 * Used by the native code (java_godot_wrapper.h) to access the input fallback mapping.
	 * @return The input fallback mapping for the current XR mode.
	 */
	@Keep
	private String getInputFallbackMapping() {
		return xrMode.inputFallbackMapping;
	}

	private void initializeGodot() {
		if (expansion_pack_path != null) {
			String[] new_cmdline;
			int cll = 0;
			if (command_line != null) {
				new_cmdline = new String[command_line.length + 2];
				cll = command_line.length;
				for (int i = 0; i < command_line.length; i++) {
					new_cmdline[i] = command_line[i];
				}
			} else {
				new_cmdline = new String[2];
			}

			new_cmdline[cll] = "--main-pack";
			new_cmdline[cll + 1] = expansion_pack_path;
			command_line = new_cmdline;
		}

		io = new GodotIO(activity);
		GodotLib.io = io;
		netUtils = new GodotNetUtils(activity);
		mSensorManager = (SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
		mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_GAME);
		mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
		mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);

		GodotLib.initialize(activity, this, activity.getAssets(), use_apk_expansion);

		result_callback = null;

		godot_initialized = true;
	}

	//	@Override
	public void onCreate(Bundle icicle) {
//		super.onCreate(icicle);

		Window window = activity.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		mClipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
		pluginRegistry = GodotPluginRegistry.initializePluginRegistry(this);

		// check for apk expansion API
		boolean md5mismatch = false;
		command_line = getCommandLine();
		String main_pack_md5 = null;
		String main_pack_key = null;

		List<String> new_args = new LinkedList<>();

		for (int i = 0; i < command_line.length; i++) {
			boolean has_extra = i < command_line.length - 1;
			if (command_line[i].equals(XRMode.REGULAR.cmdLineArg)) {
				xrMode = XRMode.REGULAR;
			} else if (command_line[i].equals(XRMode.OVR.cmdLineArg)) {
				xrMode = XRMode.OVR;
			} else if (command_line[i].equals(XRMode.OPENXR.cmdLineArg)) {
				xrMode = XRMode.OPENXR;
			} else if (command_line[i].equals("--use_depth_32")) {
				use_32_bits = true;
			} else if (command_line[i].equals("--debug_opengl")) {
				use_debug_opengl = true;
			} else if (command_line[i].equals("--translucent")) {
				translucent = true;
			} else if (command_line[i].equals("--use_immersive")) {
				use_immersive = true;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // check if the application runs on an android 4.4+
					window.getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
									View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
									View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
									View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | // hide nav bar
									View.SYSTEM_UI_FLAG_FULLSCREEN | // hide status bar
									View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

					UiChangeListener();
				}
			} else if (command_line[i].equals("--use_apk_expansion")) {
				use_apk_expansion = true;
			} else if (command_line[i].equals("--apk_expansion_path")) {
				expansion_pack_path = command_line[i + 1];
			} else if (has_extra && command_line[i].equals("--apk_expansion_md5")) {
				main_pack_md5 = command_line[i + 1];
				i++;
			} else if (has_extra && command_line[i].equals("--apk_expansion_key")) {
				main_pack_key = command_line[i + 1];
				SharedPreferences prefs = activity.getSharedPreferences("app_data_keys",
						MODE_PRIVATE);
				Editor editor = prefs.edit();
				editor.putString("store_public_key", main_pack_key);

				editor.apply();
				i++;
			} else if (command_line[i].trim().length() != 0) {
				new_args.add(command_line[i]);
			}
		}

		if (new_args.isEmpty()) {
			command_line = null;
		} else {
			command_line = new_args.toArray(new String[new_args.size()]);
		}
		if (use_apk_expansion && main_pack_md5 != null && main_pack_key != null) {
			// check that environment is ok!
			if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				// show popup and die
			}

			if (expansion_pack_path == null) {
				// Build the full path to the app's expansion files
				try {
					expansion_pack_path = Helpers.getSaveFilePath(getContext());
					expansion_pack_path += "/main." + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode + "." + activity.getPackageName() + ".obb";
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			File f = new File(expansion_pack_path);

			boolean pack_valid = true;

			if (!f.exists()) {
				pack_valid = false;

			} else if (obbIsCorrupted(expansion_pack_path, main_pack_md5)) {
				pack_valid = false;
				try {
					f.delete();
				} catch (Exception e) {
				}
			}

			if (!pack_valid) {
				Intent notifierIntent = new Intent(activity, activity.getClass());
				notifierIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

				PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0,
						notifierIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				int startResult;
				try {
					startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(
							getContext(),
							pendingIntent,
							GodotDownloaderService.class);

					if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
						// This is where you do set up to display the download
						// progress (next step in onCreateView)
/*						mDownloaderClientStub = DownloaderClientMarshaller.CreateStub(this,
								GodotDownloaderService.class);*/

						return;
					}
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
				}
			}
		}

		mCurrentIntent = activity.getIntent();

		initializeGodot();
	}

//	@Override
	public void onDestroy() {
		for (int i = 0; i < singleton_count; i++) {
			singletons[i].onMainDestroy();
		}
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			plugin.onMainDestroy();
		}

		GodotLib.ondestroy();

//		super.onDestroy();

		forceQuit();
	}

//	@Override
	public void onPause() {
//		super.onPause();
		activityResumed = false;

		if (!godot_initialized) {
			if (null != mDownloaderClientStub) {
				mDownloaderClientStub.disconnect(activity);
			}
			return;
		}
		mView.onPause();

		mSensorManager.unregisterListener(this);

		for (int i = 0; i < singleton_count; i++) {
			singletons[i].onMainPause();
		}
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			plugin.onMainPause();
		}
	}

	public String getClipboard() {
		String copiedText = "";

		if (mClipboard.getPrimaryClip() != null) {
			ClipData.Item item = mClipboard.getPrimaryClip().getItemAt(0);
			copiedText = item.getText().toString();
		}

		return copiedText;
	}

	public void setClipboard(String p_text) {
		ClipData clip = ClipData.newPlainText("myLabel", p_text);
		mClipboard.setPrimaryClip(clip);
	}

//	@Override
	public void onResume() {
//		super.onResume();
		activityResumed = true;
		if (!godot_initialized) {
			if (null != mDownloaderClientStub) {
				mDownloaderClientStub.connect(activity);
			}
			return;
		}

		mView.onResume();

		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
		mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_GAME);
		mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
		mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);

		if (use_immersive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // check if the application runs on an android 4.4+
			Window window = activity.getWindow();
			window.getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
							View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
							View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
							View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | // hide nav bar
							View.SYSTEM_UI_FLAG_FULLSCREEN | // hide status bar
							View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}

		for (int i = 0; i < singleton_count; i++) {
			singletons[i].onMainResume();
		}
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			plugin.onMainResume();
		}
	}

	public void UiChangeListener() {
		final View decorView = activity.getWindow().getDecorView();
		decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
			if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					decorView.setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
									View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
									View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
									View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
									View.SYSTEM_UI_FLAG_FULLSCREEN |
									View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
				}
			}
		});
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		Display display =
				((WindowManager)activity.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
		int displayRotation = display.getRotation();

		float[] adjustedValues = new float[3];
		final int[][] axisSwap = {
				{ 1, -1, 0, 1 }, // ROTATION_0
				{ -1, -1, 1, 0 }, // ROTATION_90
				{ -1, 1, 0, 1 }, // ROTATION_180
				{ 1, 1, 1, 0 }
		}; // ROTATION_270

		final int[] as = axisSwap[displayRotation];
		adjustedValues[0] = (float)as[0] * event.values[as[2]];
		adjustedValues[1] = (float)as[1] * event.values[as[3]];
		adjustedValues[2] = event.values[2];

		final float x = adjustedValues[0];
		final float y = adjustedValues[1];
		final float z = adjustedValues[2];

		final int typeOfSensor = event.sensor.getType();
		if (mView != null) {
			mView.queueEvent(() -> {
				if (typeOfSensor == Sensor.TYPE_ACCELEROMETER) {
					GodotLib.accelerometer(-x, y, -z);
				}
				if (typeOfSensor == Sensor.TYPE_GRAVITY) {
					GodotLib.gravity(-x, y, -z);
				}
				if (typeOfSensor == Sensor.TYPE_MAGNETIC_FIELD) {
					GodotLib.magnetometer(-x, y, -z);
				}
				if (typeOfSensor == Sensor.TYPE_GYROSCOPE) {
					GodotLib.gyroscope(x, -y, z);
				}
			});
		}
	}

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Do something here if sensor accuracy changes.
	}

	/*
	@Override public boolean dispatchKeyEvent(KeyEvent event) {

		if (event.getKeyCode()==KeyEvent.KEYCODE_BACK) {

			System.out.printf("** BACK REQUEST!\n");

			GodotLib.quit();
			return true;
		}
		System.out.printf("** OTHER KEY!\n");

		return false;
	}
	*/

	public void onBackPressed() {
		boolean shouldQuit = true;

		for (int i = 0; i < singleton_count; i++) {
			if (singletons[i].onMainBackPressed()) {
				shouldQuit = false;
			}
		}
		for (GodotPlugin plugin : pluginRegistry.getAllPlugins()) {
			if (plugin.onMainBackPressed()) {
				shouldQuit = false;
			}
		}

		if (shouldQuit && mView != null) {
			mView.queueEvent(GodotLib::back);
		}
	}

	/**
	 * Queue a runnable to be run on the render thread.
	 * <p>
	 * This must be called after the render thread has started.
	 */
	public final void runOnRenderThread(@NonNull Runnable action) {
		if (mView != null) {
			mView.queueEvent(action);
		}
	}

	public final void runOnUiThread(@NonNull Runnable action) {
		if (activity != null) {
			activity.runOnUiThread(action);
		}
	}

	private void forceQuit() {
		// TODO: This is a temp solution. The proper fix will involve tracking down and properly shutting down each
		// native Godot components that is started in Godot#onVideoInit.
		if (godotHost != null) {
			godotHost.onGodotForceQuit(this);
		}
	}

//	NOTE: the md5 check has been disabled for now
	private boolean obbIsCorrupted(String f, String main_pack_md5) {
		return false;
//		}
	}

	public boolean requestPermission(String p_name) {
		return PermissionsUtil.requestPermission(p_name, activity);
	}

	public boolean requestPermissions() {
		return PermissionsUtil.requestManifestPermissions(activity);
	}

	public String[] getGrantedPermissions() {
		return PermissionsUtil.getGrantedPermissions(activity);
	}

	public void initInputDevices() {
		mView.initInputDevices();
	}
}
