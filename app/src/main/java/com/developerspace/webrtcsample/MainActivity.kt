package com.developerspace.webrtcsample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_start.*

class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        start_meeting.setOnClickListener {
            if (meeting_id.text.toString().trim().isEmpty())
                meeting_id.error = getString(R.string.error_msg_input_box_empty)
            else
                startCall(meeting_id.text.toString().trim())
        }
        join_meeting.setOnClickListener {
            if (meeting_id.text.toString().trim().isEmpty())
                meeting_id.error = getString(R.string.error_msg_input_box_empty)
            else
                moveToRTCActivity(meeting_id.text.toString().trim(), true)
        }
    }

    private fun deleteRoom(room: String) {
        db.collection("calls")
            .document(room)
            .delete()
    }

    private fun startCall(room: String) {
        db.collection("calls")
            .document(room)
            .get()
            .addOnSuccessListener {
                when {
                    it["type"] == "OFFER" || it["type"] == "ANSWER" -> {
                        meeting_id.error = getString(R.string.error_msg_already_use_room)
                    }
                    it["type"] == "END_CALL" -> {
                        deleteRoom(room)
                        startCall(room)
                    }
                    else -> {
                        moveToRTCActivity(room, false)
                    }
                }
            }
            .addOnFailureListener {
                meeting_id.error = getString(R.string.error_msg_new_room)
            }
    }

    private fun moveToRTCActivity(room: String, isJoin: Boolean) {
        val intent = Intent(this@MainActivity, RTCActivity::class.java)
        intent.putExtra("meetingID", room)
        intent.putExtra("isJoin", isJoin)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        Constants.isCallEnded = true
    }
}