package nl.xservices.plugins;

import static android.content.Context.AUDIO_SERVICE;


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.content.BroadcastReceiver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HeadsetDetection extends CordovaPlugin {

  private static final String LOG_TAG = "HeadsetDetection";

  private static final String ACTION_DETECT = "detect";
  private static final String ACTION_EVENT = "registerRemoteEvents";
  private static final int DEFAULT_STATE = -1;
  private static final int DISCONNECTED = 0;
  private static final int CONNECTED = 1;
  protected static CordovaWebView mCachedWebView = null;

  BroadcastReceiver receiver;

  public HeadsetDetection() {
      this.receiver = null;
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
      super.initialize(cordova, webView);
      mCachedWebView = webView;
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
      intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
      this.receiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
              int status = getConnectionStatus(intent.getAction(), intent);

              if (status == CONNECTED) {
                Log.d(LOG_TAG, "Headset is connected");
                mCachedWebView.sendJavascript("cordova.require('cordova-plugin-headsetdetection.HeadsetDetection').remoteHeadsetAdded();");
              } else if (status == DISCONNECTED) {
                Log.d(LOG_TAG, "Headset is disconnected");
                mCachedWebView.sendJavascript("cordova.require('cordova-plugin-headsetdetection.HeadsetDetection').remoteHeadsetRemoved();");
              } else {
                Log.d(LOG_TAG, "Headset state is unknown: " + status);
              }
          }
      };
      mCachedWebView.getContext().registerReceiver(this.receiver, intentFilter);

      AudioManager manager = (AudioManager) webView.getContext().getSystemService(AUDIO_SERVICE);
      manager.registerAudioDeviceCallback(
              new AudioDeviceCallback() {
                  @Override
                  public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                      Log.d(LOG_TAG, "onAudioDevicesAdded: ");
                      Log.d(LOG_TAG, "Headset is connected");
                      mCachedWebView.sendJavascript("cordova.require('cordova-plugin-headsetdetection.HeadsetDetection').remoteHeadsetAdded();");
                      super.onAudioDevicesAdded(addedDevices);
                  }

                  @Override
                  public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                      Log.d(LOG_TAG, "Headset is disconnected");
                      Log.d(LOG_TAG, "onAudioDevicesRemoved: ");
                      mCachedWebView.sendJavascript("cordova.require('cordova-plugin-headsetdetection.HeadsetDetection').remoteHeadsetRemoved();");
                      super.onAudioDevicesRemoved(removedDevices);
                  }
              }, null
      );
  }



  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
      Log.d(LOG_TAG, "execute: action:" + action);
    try {
      if (ACTION_DETECT.equals(action) || ACTION_EVENT.equals(action)) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, isHeadsetEnabled()));
        return true;
      } else {
        callbackContext.error("headsetdetection." + action + " is not a supported function. Did you mean '" + ACTION_DETECT + "'?");
        return false;
      }
    } catch (Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
  }

  private boolean isHeadsetEnabled() {
    final AudioManager audioManager = (AudioManager) cordova.getActivity().getSystemService(AUDIO_SERVICE);
    return audioManager.isWiredHeadsetOn() ||
        audioManager.isBluetoothA2dpOn() ||
        audioManager.isBluetoothScoOn();
  }

  public void onDestroy() {
      removeHeadsetListener();
  }

  public void onReset() {
      removeHeadsetListener();
  }

  private int getConnectionStatus(String action, Intent intent) {
      int state = DEFAULT_STATE;
      int normalizedState = DEFAULT_STATE;
      if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
        state = intent.getIntExtra("state", DEFAULT_STATE);
      } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
        state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, DEFAULT_STATE);
      }

      if ((state == 1 && action.equals(Intent.ACTION_HEADSET_PLUG)) || (state == 2 && action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))) {
          normalizedState = CONNECTED;
      } else if (state == 0) {
          normalizedState = DISCONNECTED;
      }

      return normalizedState;
  }

  private void removeHeadsetListener() {
      if (this.receiver != null) {
          try {
              mCachedWebView.getContext().unregisterReceiver(this.receiver);
              this.receiver = null;
          } catch (Exception e) {
              Log.e(LOG_TAG, "Error unregistering battery receiver: " + e.getMessage(), e);
          }
      }
  }
}