package com.example.faceswap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face


/**
 * Main activity for FaceSwap.
 *
 * @author alex011235
 */
class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private val pickImage = 100
    private var selectedTab = 0

    private val desiredWidth = 800
    private val desiredHeight = 800

    private var imageUriFace1: Uri? = null
    private var imageUriFace2: Uri? = null

    private lateinit var bitmap1: Bitmap
    private lateinit var bitmap2: Bitmap
    private lateinit var bitmap1Swapped: Bitmap
    private lateinit var bitmap2Swapped: Bitmap

    private lateinit var imageView: ImageView
    private lateinit var button: FloatingActionButton
    private lateinit var tabLayout: TabLayout

    private lateinit var faces1: List<Face>
    private lateinit var faces2: List<Face>
    private val faceDetectorEngine = FaceDetectorEngine()

    private var face1Done = false
    private var face2Done = false
    private var okToSwap = false
    private var hasSwapped = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            button.isEnabled = false

            if (selectedTab == 0) {
                imageUriFace1 = uri
                imageView.setImageURI(imageUriFace1)
                imageUriFace1?.let { prepareImage(it, 0) }
            }
            if (selectedTab == 1) {
                imageUriFace2 = uri
                imageView.setImageURI(imageUriFace2)
                imageUriFace2?.let { prepareImage(it, 1) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        button = findViewById(R.id.fab)
        button.isEnabled = false
        imageView = findViewById(R.id.imageView)
        tabLayout = tabs

        // Change tabs
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    Log.d(tag, "Tab ${tab.position} selected")
                    selectedTab = tab.position

                    if (hasSwapped) {
                        // Swapped, used swapped bitmaps instead of source.
                        if (tab.position == 0) {
                            imageView.setImageBitmap(bitmap1Swapped)
                        }
                        if (tab.position == 1) {
                            imageView.setImageBitmap(bitmap2Swapped)
                        }
                    } else {
                        // Has not swapped, use sources.
                        if (tab.position == 0) {
                            imageView.setImageURI(imageUriFace1)
                        }
                        if (tab.position == 1) {
                            imageView.setImageURI(imageUriFace2)
                        }
                    }
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                Log.d(tag, "onTabReselected not in use.")
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                Log.d(tag, "onTabUnselected not in use.")
            }
        })

        // Open gallery for image selection

        imageView.setOnClickListener {
            Log.d(tag, "Click on image view.")
            pickImageLauncher.launch("image/*")
        }

        // Click listener for action button, should result in face swap.

        button.setOnClickListener {
            Log.d(tag, "Action button clicked.")

            if (okToSwap) {
                Log.d(tag, "Ready to swap!")

                val landmarksForFaces1 = Landmarks.arrangeLandmarksForFaces(faces1)
                val landmarksForFaces2 = Landmarks.arrangeLandmarksForFaces(faces2)

                bitmap2Swapped =
                    Swap.faceSwapAll(bitmap1, bitmap2, landmarksForFaces1, landmarksForFaces2)
                bitmap1Swapped =
                    Swap.faceSwapAll(bitmap2, bitmap1, landmarksForFaces2, landmarksForFaces1)


                if (selectedTab == 0) {
                    imageView.setImageBitmap(bitmap1Swapped)
                }

                if (selectedTab == 1) {
                    imageView.setImageBitmap(bitmap2Swapped)
                }

                hasSwapped = true

                // imageUriFace1?.let { it1 -> drawLandmarks(it1, landmarksForFaces1) }
            }
        }
    }

    /**
     * Prepares chosen image for face detection.
     *
     * @param uri Location to image.
     */
    private fun prepareImage(uri: Uri, faceIndex: Int) {
        Log.d(tag, "prepareImage: Preparing image for face detection.")

        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : CustomTarget<Bitmap>(desiredWidth, desiredHeight) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {

                    val inputImage = InputImage.fromBitmap(resource, 0)
                    hasSwapped = false

                    when (faceIndex) {
                        0 -> bitmap1 = resource
                        else -> bitmap2 = resource
                    }

                    faceDetectorEngine.detectInImage(inputImage)
                        .addOnSuccessListener { faces ->
                            when (faceIndex) {
                                0 -> faces1 = faces
                                else -> faces2 = faces
                            }

                            val notEmpty = faces.isNotEmpty()
                            if (notEmpty && faceIndex == 0) {
                                face1Done = true
                            }
                            if (notEmpty && faceIndex == 1) {
                                face2Done = true
                            }

                            okToSwap = face1Done && face2Done
                            button.isEnabled = okToSwap
                        }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    /**
     * Draws landmarks for a face. Only for debugging.
     *
     * @param uri Image source.
     * @param landmarksForFaces All landmarks extracted in source image.
     */
    private fun drawLandmarks(uri: Uri, landmarksForFaces: ArrayList<ArrayList<PointF>>) {
        Log.v(tag, "Draw landmarks for faces.")

        Glide.with(this)
            .asBitmap()
            .load(uri)
            .into(object : CustomTarget<Bitmap>(desiredWidth, desiredHeight) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val bitmapWithLandmarks =
                        ImageUtils.drawLandmarksOnBitmap(resource, landmarksForFaces)

                    imageView.setImageBitmap(bitmapWithLandmarks)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

}
