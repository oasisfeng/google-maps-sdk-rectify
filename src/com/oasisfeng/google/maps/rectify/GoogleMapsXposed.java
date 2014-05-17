package com.oasisfeng.google.maps.rectify;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Set;

import android.os.Debug;
import android.util.Log;
import android.util.Pair;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** @author Oasis */
public class GoogleMapsXposed implements IXposedHookLoadPackage {

	private static final boolean DEBUG = false;

	@Override public void handleLoadPackage(final LoadPackageParam loadpkg) throws Throwable {
		final Class<?> LatLng;
		Constructor<?> LatLng_ctor;
		final long start = DEBUG ? Debug.threadCpuTimeNanos() : 0;
		try {
			LatLng = Class.forName("com.google.android.gms.maps.model.LatLng", false, loadpkg.classLoader);
			// public LatLng(double latitude, double longitude)
			LatLng_ctor = LatLng.getConstructor(double.class, double.class);
		} catch (final Throwable e) {
			if (DEBUG) {
				final long end = Debug.threadCpuTimeNanos();
				Log.d(TAG, "Load time for skipped process: " + (end - start) + "ns");
			}
			return;
		}
		Log.i(TAG, "Patching Google Maps SDK v2 in " + loadpkg.packageName);

		XposedBridge.hookMethod(LatLng_ctor, new XC_MethodHook() {

			@Override protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
				final String skip_reason = mSkip.get();
				if (skip_reason != null) {
					Log.d(TAG, "Skip rectifying for " + skip_reason);
					return;
				}
				final Double latitude = (Double) param.args[0];
				final Double longitude = (Double) param.args[1];

				if (mRecent.contains(new Pair<>(latitude, longitude).hashCode())) return;	// Duplicate

				final Pair<Double, Double> trans = Transform.transform(latitude, longitude);
				param.args[0] = trans.first;
				param.args[1] = trans.second;

				mRecent.add(trans.hashCode());

				if (DEBUG) {
					final Formatter formatter = new Formatter();
					Log.d(TAG, formatter.format("%.4f,%.4f => %.4f,%.4f", latitude, longitude, param.args[0], param.args[1]).toString()/*, new Throwable()*/);
					formatter.close();
				}
			}
		});

		// Skip duplicate rectify in following methods:
		//	LatLngBounds$Builder.build()
		//	LatLngBounds.including(LatLng)
		//  LatLngBounds.getCenter()
		try {
			XposedHelpers.findAndHookMethod("com.google.android.gms.maps.model.LatLngBounds$Builder", loadpkg.classLoader, "build", new XC_MethodHook() {

				@Override protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set("LatLngBounds.Builder.build()");
				}

				@Override protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set(null);
				}
			});
		} catch (ClassNotFoundError | NoSuchMethodError e) {}
		try {
			XposedHelpers.findAndHookMethod("com.google.android.gms.maps.model.LatLngBounds", loadpkg.classLoader, "including", LatLng, new XC_MethodHook() {

				@Override protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set("LatLngBounds.including()");
				}

				@Override protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set(null);
				}
			});
		} catch (ClassNotFoundError | NoSuchMethodError e) {}
		try {
			XposedHelpers.findAndHookMethod("com.google.android.gms.maps.model.LatLngBounds", loadpkg.classLoader, "getCenter", new XC_MethodHook() {

				@Override protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set("LatLngBounds.getCenter()");
				}

				@Override protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
					mSkip.set(null);
				}
			});
		} catch (ClassNotFoundError | NoSuchMethodError e) {}
	}

	private final Set<Integer> mRecent = Collections.synchronizedSet(Collections.newSetFromMap(new Cache<Integer, Boolean>()));
	static ThreadLocal<String> mSkip = new ThreadLocal<String>();

	static final String TAG = "GoogleMapsXposed";
}

class Cache<K, V> extends LinkedHashMap<K, V> {

	private static final int KMaxEntries = 128;

	@Override protected boolean removeEldestEntry(final java.util.Map.Entry<K, V> eldest) {
		return (size() > KMaxEntries);
	}

	private static final long serialVersionUID = 1L;
}