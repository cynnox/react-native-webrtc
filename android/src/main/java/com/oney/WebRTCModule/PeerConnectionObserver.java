package com.oney.WebRTCModule;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;

class PeerConnectionObserver implements PeerConnection.Observer {
    private final static String TAG = WebRTCModule.TAG;

    private final SparseArray<DataChannel> dataChannels
        = new SparseArray<DataChannel>();
    private final int id;
    private PeerConnection peerConnection;
    private final WebRTCModule webRTCModule;

    PeerConnectionObserver(WebRTCModule webRTCModule, int id) {
        this.webRTCModule = webRTCModule;
        this.id = id;
    }

    PeerConnection getPeerConnection() {
        return peerConnection;
    }

    void setPeerConnection(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    void createDataChannel(String label, ReadableMap config) {
        DataChannel.Init init = new DataChannel.Init();
        if (config != null) {
            if (config.hasKey("id")) {
                init.id = config.getInt("id");
            }
            if (config.hasKey("ordered")) {
                init.ordered = config.getBoolean("ordered");
            }
            if (config.hasKey("maxRetransmitTime")) {
                init.maxRetransmitTimeMs = config.getInt("maxRetransmitTime");
            }
            if (config.hasKey("maxRetransmits")) {
                init.maxRetransmits = config.getInt("maxRetransmits");
            }
            if (config.hasKey("protocol")) {
                init.protocol = config.getString("protocol");
            }
            if (config.hasKey("negotiated")) {
                init.negotiated = config.getBoolean("negotiated");
            }
        }
        DataChannel dataChannel = peerConnection.createDataChannel(label, init);
        // XXX RTP data channels are not defined by the WebRTC standard, have
        // been deprecated in Chromium, and Google have decided (in 2015) to no
        // longer support them (in the face of multiple reported issues of
        // breakages).
        int dataChannelId = init.id;
        if (-1 != dataChannelId) {
            dataChannels.put(dataChannelId, dataChannel);
            registerDataChannelObserver(dataChannelId, dataChannel);
        }
    }

    void dataChannelClose(int dataChannelId) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            dataChannel.close();
            dataChannels.remove(dataChannelId);
        } else {
            Log.d(TAG, "dataChannelClose() dataChannel is null");
        }
    }

    void dataChannelSend(int dataChannelId, String data, String type) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            byte[] byteArray;
            if (type.equals("text")) {
                try {
                    byteArray = data.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, "Could not encode text string as UTF-8.");
                    return;
                }
            } else if (type.equals("binary")) {
                byteArray = Base64.decode(data, Base64.NO_WRAP);
            } else {
                Log.e(TAG, "Unsupported data type: " + type);
                return;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
            DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, type.equals("binary"));
            dataChannel.send(buffer);
        } else {
            Log.d(TAG, "dataChannelSend() dataChannel is null");
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        Log.d(TAG, "onIceCandidatewqerqwrsdfsd");
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        WritableMap candidateParams = Arguments.createMap();
        candidateParams.putInt("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateParams.putString("sdpMid", candidate.sdpMid);
        candidateParams.putString("candidate", candidate.sdp);
        params.putMap("candidate", candidateParams);

        webRTCModule.sendEvent("peerConnectionGotICECandidate", params);
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        Log.d(TAG, "onIceCandidatesRemoved");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("iceConnectionState", iceConnectionStateString(iceConnectionState));

        webRTCModule.sendEvent("peerConnectionIceConnectionChanged", params);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean var1) {
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChangedwe" + iceGatheringState.name());
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("iceGatheringState", iceGatheringStateString(iceGatheringState));
        webRTCModule.sendEvent("peerConnectionIceGatheringChanged", params);
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        String streamReactTag = webRTCModule.onAddStream(mediaStream);

        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("streamId", mediaStream.label());
        params.putString("streamReactTag", streamReactTag);

        WritableArray tracks = Arguments.createArray();

        for (int i = 0; i < mediaStream.videoTracks.size(); i++) {
            VideoTrack track = mediaStream.videoTracks.get(i);

            WritableMap trackInfo = Arguments.createMap();
            trackInfo.putString("id", track.id());
            trackInfo.putString("label", "Video");
            trackInfo.putString("kind", track.kind());
            trackInfo.putBoolean("enabled", track.enabled());
            trackInfo.putString("readyState", track.state().toString());
            trackInfo.putBoolean("remote", true);
            tracks.pushMap(trackInfo);
        }
        for (int i = 0; i < mediaStream.audioTracks.size(); i++) {
            AudioTrack track = mediaStream.audioTracks.get(i);

            WritableMap trackInfo = Arguments.createMap();
            trackInfo.putString("id", track.id());
            trackInfo.putString("label", "Audio");
            trackInfo.putString("kind", track.kind());
            trackInfo.putBoolean("enabled", track.enabled());
            trackInfo.putString("readyState", track.state().toString());
            trackInfo.putBoolean("remote", true);
            tracks.pushMap(trackInfo);
        }
        params.putArray("tracks", tracks);

        webRTCModule.sendEvent("peerConnectionAddedStream", params);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        String streamReactTag = webRTCModule.onRemoveStream(mediaStream);
        if (streamReactTag == null) {
            return;
        }
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("streamId", streamReactTag);
        webRTCModule.sendEvent("peerConnectionRemovedStream", params);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        // XXX Unfortunately, the Java WebRTC API doesn't expose the id
        // of the underlying C++/native DataChannel (even though the
        // WebRTC standard defines the DataChannel.id property). As a
        // workaround, generated an id which will surely not clash with
        // the ids of the remotely-opened (and standard-compliant
        // locally-opened) DataChannels.
        int dataChannelId = -1;
        // The RTCDataChannel.id space is limited to unsigned short by
        // the standard:
        // https://www.w3.org/TR/webrtc/#dom-datachannel-id.
        // Additionally, 65535 is reserved due to SCTP INIT and
        // INIT-ACK chunks only allowing a maximum of 65535 streams to
        // be negotiated (as defined by the WebRTC Data Channel
        // Establishment Protocol).
        for (int i = 65536; i <= Integer.MAX_VALUE; ++i) {
            if (null == dataChannels.get(i, null)) {
                dataChannelId = i;
                break;
            }
        }
        if (-1 == dataChannelId) {
          return;
        }

        WritableMap dataChannelParams = Arguments.createMap();
        dataChannelParams.putInt("id", dataChannelId);
        dataChannelParams.putString("label", dataChannel.label());
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putMap("dataChannel", dataChannelParams);

        dataChannels.put(dataChannelId, dataChannel);
        registerDataChannelObserver(dataChannelId, dataChannel);

        webRTCModule.sendEvent("peerConnectionDidOpenDataChannel", params);
    }

    private void registerDataChannelObserver(int dcId, DataChannel dataChannel) {
        // DataChannel.registerObserver implementation does not allow to
        // unregister, so the observer is registered here and is never
        // unregistered
        dataChannel.registerObserver(
            new DataChannelObserver(webRTCModule, id, dcId, dataChannel));
    }

    @Override
    public void onRenegotiationNeeded() {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        webRTCModule.sendEvent("peerConnectionOnRenegotiationNeeded", params);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("signalingState", signalingStateString(signalingState));
        webRTCModule.sendEvent("peerConnectionSignalingStateChanged", params);
    }

    @Nullable
    private String iceConnectionStateString(PeerConnection.IceConnectionState iceConnectionState) {
        switch (iceConnectionState) {
            case NEW:
                return "new";
            case CHECKING:
                return "checking";
            case CONNECTED:
                return "connected";
            case COMPLETED:
                return "completed";
            case FAILED:
                return "failed";
            case DISCONNECTED:
                return "disconnected";
            case CLOSED:
                return "closed";
        }
        return null;
    }

    @Nullable
    private String iceGatheringStateString(PeerConnection.IceGatheringState iceGatheringState) {
        switch (iceGatheringState) {
            case NEW:
                return "new";
            case GATHERING:
                return "gathering";
            case COMPLETE:
                return "complete";
        }
        return null;
    }

    @Nullable
    private String signalingStateString(PeerConnection.SignalingState signalingState) {
        switch (signalingState) {
            case STABLE:
                return "stable";
            case HAVE_LOCAL_OFFER:
                return "have-local-offer";
            case HAVE_LOCAL_PRANSWER:
                return "have-local-pranswer";
            case HAVE_REMOTE_OFFER:
                return "have-remote-offer";
            case HAVE_REMOTE_PRANSWER:
                return "have-remote-pranswer";
            case CLOSED:
                return "closed";
        }
        return null;
    }
}