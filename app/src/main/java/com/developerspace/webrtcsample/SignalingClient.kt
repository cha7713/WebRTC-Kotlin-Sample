package com.developerspace.webrtcsample

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@ExperimentalCoroutinesApi
class SignalingClient(
    private val meetingID: String,
    private val isJoin: Boolean,
    private val listener: SignalingClientListener
) : CoroutineScope {

    private val job = Job()

    private val TAG = this.javaClass.simpleName

    private val db = Firebase.firestore

    override val coroutineContext = Dispatchers.IO + job

    private val sendChannel = ConflatedBroadcastChannel<String>()

    private val serverUrlList = arrayListOf<String>()
    private val sdpMidList = arrayListOf<String>()
    private val sdpMLineIndexList = arrayListOf<String>()
    private val sdpCandidateList = arrayListOf<String>()

    init {
        connect()
    }

    private fun connect() = launch {
        db.enableNetwork().addOnSuccessListener {
            listener.onConnectionEstablished()
        }
        val sendData = sendChannel.offer("")
        sendData.let {
            Log.v(this@SignalingClient.javaClass.simpleName, "Sending: $it")
        }
        try {
            db.collection("calls")
                .document(meetingID)
                .addSnapshotListener { snapshot, e ->

                    if (e != null) {
                        Log.w(TAG, "listen:error", e)
                        return@addSnapshotListener
                    }

                    snapshot?.data?.let { data ->
                        when {
                            data.containsKey("type") && data.getValue("type").toString() == "OFFER" -> {
                                listener.onOfferReceived(SessionDescription(SessionDescription.Type.OFFER, data["sdp"].toString()))
                            }
                            data.containsKey("type") && data.getValue("type").toString() == "ANSWER" -> {
                                listener.onAnswerReceived(SessionDescription(SessionDescription.Type.ANSWER, data["sdp"].toString()))
                            }
                            data.containsKey("type") && data.getValue("type").toString() == "END_CALL" -> {
                                listener.onCallEnded()
                            }
                        }
                        Log.d(TAG, "Current data: ${snapshot.data}")
                    }

                }
            db.collection("calls").document(meetingID)
                .collection("candidates").addSnapshotListener { querySnapshot, e ->
                    if (e != null) {
                        Log.w(TAG, "listen:error", e)
                        return@addSnapshotListener
                    }
                    querySnapshot?.forEach { dataSnapshot ->
                        val data = dataSnapshot.data
                        Log.d(TAG, data.toString())
                        if (isJoin && data.containsKey("type") && data["type"] == "offerCandidate") {
                            sendIceCandidatesToListener(data)
                            // offerCandidate를 수신한 이후 answerCandidate 전송
                            sendIceCandidate(true)
                        } else if (!isJoin && data.containsKey("type") && data["type"] == "answerCandidate") {
                            sendIceCandidatesToListener(data)
                        }
                        Log.e(TAG, "candidateQuery: $dataSnapshot")
                    }
                }
        } catch (exception: Exception) {
            Log.e(TAG, "connectException: $exception")

        }
    }

    fun sendIceCandidate(isJoin: Boolean) = runBlocking {
        val type = when {
            isJoin -> "answerCandidate"
            else -> "offerCandidate"
        }
        val candidateConstant = hashMapOf(
            "serverUrl" to serverUrlList,
            "sdpMid" to sdpMidList,
            "sdpMLineIndex" to sdpMLineIndexList,
            "sdpCandidate" to sdpCandidateList,
            "type" to type
        )
        db.collection("calls")
            .document("$meetingID").collection("candidates").document(type)
            .set(candidateConstant as Map<String, Any>)
            .addOnSuccessListener {
                Log.e(TAG, "sendIceCandidate: Success")
            }
            .addOnFailureListener {
                Log.e(TAG, "sendIceCandidate: Error $it")
            }
    }

    fun stackIceCandidate(candidate: IceCandidate?) = runBlocking {
        candidate?.let {
            serverUrlList.add(candidate.serverUrl ?: "")
            sdpMidList.add(candidate.sdpMid ?: "")
            sdpMLineIndexList.add(candidate.sdpMLineIndex.toString())
            sdpCandidateList.add(candidate.sdp ?: "")
        }
    }

    private fun sendIceCandidatesToListener(data: MutableMap<String, Any>) {
        val sdpMLineIndex = data["sdpMLineIndex"] as List<String>
        val sdpMid = data["sdpMid"] as List<String>
        val sdpCandidate = data["sdpCandidate"] as List<String>
        for (i in 0..sdpCandidate.lastIndex) {
            listener.onIceCandidateReceived(
                IceCandidate(
                    sdpMid[i],
                    Math.toIntExact(sdpMLineIndex[i].toLong()),
                    sdpCandidate[i]
                )
            )
        }
    }

    fun destroy() {
        job.complete()
    }
}
