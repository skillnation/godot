/*************************************************************************/
/*  GodotApp.java                                                        */
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

package com.godot.game;

import android.app.Activity;
import android.os.Bundle;

import org.godotengine.godot.GodotHost;

import java.util.Arrays;
import java.util.List;

/**
 * Template activity for Godot Android custom builds.
 * Feel free to extend and modify this class for your custom logic.
 */
public class GodotApp extends Activity implements GodotHost {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.GodotAppMainTheme);
		super.onCreate(savedInstanceState);
	}

	@Override
	public List<String> getCommandLine() {
		return Arrays.asList(
				"--use_apk_expansion",
				"--apk_expansion_md5",
				ParameterInstance.md5,
				"--apk_expansion_path",
				ParameterInstance.URI,
				"--apk_expansion_key",
				"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk29A0Rdw7B67IjcoLhJ/FKzi1uNiFOqQO3xQrDyLUBf/3VKo/GEujzA6MRlkGeDLaYtlfEpO8PcJSNk5oktN7RyUkCPN3623Fj7oDXTayVgdiunQgEGiqS1f22DYQgoV+BJ8GuaQfDBHrdimmfDLnyyYXt58/x93QT5PQSoRB3Ygl70Yb97fv9QLgGDUoJUomlsvVpp2Z0YUxr8jRIBpbiQNwgSMRHtg+UHatSpnpH4mHj9HsOAQA2y4/BtV8EaFA1Yy5CFX9pK8X2Yij20J+cyyFY6801I6b+K6dzAJzBqqV1XUE6xGDjuhKX+QyHUTpEF+nj4vTHSUjn7kh1BdkwIDAQAB",
				"--xr_mode_regular",
				"--use_depth_32",
				"--use_immersive"
		);
	}
}
