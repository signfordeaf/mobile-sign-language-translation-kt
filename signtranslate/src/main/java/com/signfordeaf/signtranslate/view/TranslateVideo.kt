package com.signfordeaf.signtranslate.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import com.signfordeaf.signtranslate.databinding.BottomSheetLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class  TranslateVideo(
    private val videoUrl: String
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var videoView: VideoView

    private fun videoView() {
        videoView = binding.videoView
        val videoUrl = videoUrl
        videoView.setVideoPath(videoUrl)
        videoView.setOnPreparedListener {
            videoView.start()
        }
        val mediaController = MediaController(requireContext())
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(null)
        videoView.setOnCompletionListener { videoView.start() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoView()
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        binding.videoView.setOnClickListener {
            videoView.start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
