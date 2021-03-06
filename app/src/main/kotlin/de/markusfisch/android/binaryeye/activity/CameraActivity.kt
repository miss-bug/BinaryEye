package de.markusfisch.android.binaryeye.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.support.v8.renderscript.RSRuntimeException
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.ResultPointCallback
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.app.*
import de.markusfisch.android.binaryeye.data.Scan
import de.markusfisch.android.binaryeye.graphics.Mapping
import de.markusfisch.android.binaryeye.graphics.frameToView
import de.markusfisch.android.binaryeye.rs.Preprocessor
import de.markusfisch.android.binaryeye.widget.DetectorView
import de.markusfisch.android.binaryeye.widget.toast
import de.markusfisch.android.binaryeye.zxing.Zxing
import de.markusfisch.android.cameraview.widget.CameraView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CameraActivity : AppCompatActivity() {
	private val zxing = Zxing(ResultPointCallback { point ->
		point?.let {
			mapping?.map(it)?.let {
				detectorView.post {
					detectorView.mark(listOf(it))
				}
			}
		}
	})

	private lateinit var vibrator: Vibrator
	private lateinit var cameraView: CameraView
	private lateinit var detectorView: DetectorView
	private lateinit var zoomBar: SeekBar
	private lateinit var flashFab: View

	private var preprocessor: Preprocessor? = null
	private var mapping: Mapping? = null
	private var invert = false
	private var flash = false
	private var returnResult = false
	private var frontFacing = false
	private var fallbackBuffer: IntArray? = null

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		when (requestCode) {
			REQUEST_CAMERA -> if (grantResults.isNotEmpty() &&
				grantResults[0] != PackageManager.PERMISSION_GRANTED
			) {
				toast(R.string.no_camera_no_fun)
				finish()
			}
		}
	}

	override fun onActivityResult(
		requestCode: Int,
		resultCode: Int,
		resultData: Intent?
	) {
		when (requestCode) {
			PICK_FILE_RESULT_CODE -> {
				if (resultCode == Activity.RESULT_OK && resultData != null) {
					val pick = Intent(this, PickActivity::class.java)
					pick.action = Intent.ACTION_VIEW
					pick.setDataAndType(resultData.data, "image/*")
					startActivity(pick)
				}
			}
		}
	}

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_camera)

		vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

		initSystemBars(this)
		setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)

		cameraView = findViewById(R.id.camera_view) as CameraView
		detectorView = findViewById(R.id.detector_view) as DetectorView
		zoomBar = findViewById(R.id.zoom) as SeekBar
		flashFab = findViewById(R.id.flash)
		flashFab.setOnClickListener { toggleTorchMode() }

		initCameraView()
		initZoomBar()
		restoreZoom()

		if (intent?.action == Intent.ACTION_SEND &&
			intent.type == "text/plain"
		) {
			handleSendText(intent)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		resetPreProcessor()
		fallbackBuffer = null
		saveZoom()
	}

	override fun onResume() {
		super.onResume()
		System.gc()
		zxing.updateHints(prefs.tryHarder)
		returnResult = "com.google.zxing.client.android.SCAN" == intent.action
		if (hasCameraPermission(this, REQUEST_CAMERA)) {
			openCamera()
		}
	}

	private fun resetPreProcessor() {
		preprocessor?.destroy()
		preprocessor = null
	}

	private fun openCamera() {
		cameraView.openAsync(
			CameraView.findCameraId(
				@Suppress("DEPRECATION")
				if (frontFacing) {
					Camera.CameraInfo.CAMERA_FACING_FRONT
				} else {
					Camera.CameraInfo.CAMERA_FACING_BACK
				}
			)
		)
	}

	override fun onPause() {
		super.onPause()
		closeCamera()
	}

	private fun closeCamera() {
		cameraView.close()
		// closing the camera will also shut off the flash
		flash = false
	}

	override fun onRestoreInstanceState(savedState: Bundle) {
		super.onRestoreInstanceState(savedState)
		zoomBar.max = savedState.getInt(ZOOM_MAX)
		zoomBar.progress = savedState.getInt(ZOOM_LEVEL)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putInt(ZOOM_MAX, zoomBar.max)
		outState.putInt(ZOOM_LEVEL, zoomBar.progress)
		super.onSaveInstanceState(outState)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.activity_camera, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.create -> {
				startActivity(MainActivity.getEncodeIntent(this))
				true
			}
			R.id.history -> {
				startActivity(MainActivity.getHistoryIntent(this))
				true
			}
			R.id.pick_file -> {
				val chooseFile = Intent(Intent.ACTION_GET_CONTENT)
				chooseFile.type = "image/*"
				startActivityForResult(
					Intent.createChooser(
						chooseFile,
						getString(R.string.pick_file)
					),
					PICK_FILE_RESULT_CODE
				)
				true
			}
			R.id.switch_camera -> {
				switchCamera()
				true
			}
			R.id.preferences -> {
				startActivity(MainActivity.getPreferencesIntent(this))
				true
			}
			R.id.info -> {
				openReadme()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun switchCamera() {
		closeCamera()
		frontFacing = frontFacing xor true
		openCamera()
	}

	private fun openReadme() {
		val intent = Intent(
			Intent.ACTION_VIEW,
			Uri.parse(getString(R.string.project_url))
		)
		if (intent.resolveActivity(packageManager) != null) {
			startActivity(intent)
		} else {
			toast(R.string.project_url)
		}
	}

	private fun handleSendText(intent: Intent) {
		val text = intent.getStringExtra(Intent.EXTRA_TEXT)
		if (text?.isEmpty() == false) {
			startActivity(MainActivity.getEncodeIntent(this, text, true))
			finish()
		}
	}

	private fun initCameraView() {
		cameraView.setUseOrientationListener(true)
		@Suppress("ClickableViewAccessibility")
		cameraView.setOnTouchListener(object : View.OnTouchListener {
			var focus = true
			var offset = -1f
			var progress = 0

			override fun onTouch(v: View?, event: MotionEvent?): Boolean {
				event ?: return false
				val pos = event.y
				when (event.actionMasked) {
					MotionEvent.ACTION_DOWN -> {
						offset = pos
						progress = zoomBar.progress
						return true
					}
					MotionEvent.ACTION_MOVE -> {
						if (prefs.zoomBySwiping) {
							v ?: return false
							val dist = offset - pos
							val maxValue = zoomBar.max
							val change = maxValue / v.height.toFloat() * 2f * dist
							zoomBar.progress = min(
								maxValue,
								max(progress + change.roundToInt(), 0)
							)
							return true
						}
					}
					MotionEvent.ACTION_UP -> {
						// stop calling focusTo() as soon as it returns false
						// to avoid throwing and catching future exceptions
						if (focus) {
							focus = cameraView.focusTo(v, event.x, event.y)
							if (focus) {
								v?.performClick()
								return true
							}
						}
					}
				}
				return false
			}
		})
		@Suppress("DEPRECATION")
		cameraView.setOnCameraListener(object : CameraView.OnCameraListener {
			override fun onConfigureParameters(
				parameters: Camera.Parameters
			) {
				if (parameters.isZoomSupported) {
					val max = parameters.maxZoom
					if (zoomBar.max != max) {
						zoomBar.max = max
						zoomBar.progress = max / 10
						saveZoom()
					}
					parameters.zoom = zoomBar.progress
				} else {
					zoomBar.visibility = View.GONE
				}
				val sceneModes = parameters.supportedSceneModes
				sceneModes?.let {
					for (mode in sceneModes) {
						if (mode == Camera.Parameters.SCENE_MODE_BARCODE) {
							parameters.sceneMode = mode
							break
						}
					}
				}
				CameraView.setAutoFocus(parameters)
				updateFlashFab(parameters.flashMode == null)
			}

			override fun onCameraError() {
				this@CameraActivity.toast(R.string.camera_error)
				finish()
			}

			override fun onCameraReady(camera: Camera) {
				val frameWidth = cameraView.frameWidth
				val frameHeight = cameraView.frameHeight
				val frameOrientation = cameraView.frameOrientation
				var decoding = true
				// reset preprocessor to make sure it fits the current
				// frame orientation; important for landscape to landscape
				// orientation changes
				resetPreProcessor()
				camera.setPreviewCallback { frameData, _ ->
					if (decoding) {
						decodeFrame(
							frameData,
							frameWidth,
							frameHeight,
							frameOrientation
						)?.let { result ->
							postResult(result)
							decoding = false
						}
					}
				}
			}

			override fun onPreviewStarted(camera: Camera) {
			}

			override fun onCameraStopping(camera: Camera) {
				camera.setPreviewCallback(null)
			}
		})
	}

	private fun initZoomBar() {
		zoomBar.setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(
				seekBar: SeekBar,
				progress: Int,
				fromUser: Boolean
			) {
				setZoom(progress)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}

			override fun onStopTrackingTouch(seekBar: SeekBar) {}
		})
	}

	@Suppress("DEPRECATION")
	private fun setZoom(zoom: Int) {
		val camera: Camera? = cameraView.camera
		camera?.let {
			try {
				val params = camera.parameters
				params.zoom = zoom
				camera.parameters = params
			} catch (e: RuntimeException) {
				// ignore; there's nothing we can do
			}
		}
	}

	private fun saveZoom() {
		val editor = prefs.preferences.edit()
		editor.putInt(ZOOM_MAX, zoomBar.max)
		editor.putInt(ZOOM_LEVEL, zoomBar.progress)
		editor.apply()
	}

	private fun restoreZoom() {
		zoomBar.max = prefs.preferences.getInt(ZOOM_MAX, zoomBar.max)
		zoomBar.progress = prefs.preferences.getInt(
			ZOOM_LEVEL,
			zoomBar.progress
		)
	}

	private fun updateFlashFab(available: Boolean) {
		flashFab.visibility = if (available) {
			View.GONE
		} else {
			View.VISIBLE
		}
	}

	@Suppress("DEPRECATION")
	private fun toggleTorchMode() {
		val camera = cameraView.camera
		val parameters = camera?.parameters
		parameters?.flashMode = if (flash) {
			Camera.Parameters.FLASH_MODE_OFF
		} else {
			Camera.Parameters.FLASH_MODE_TORCH
		}
		parameters?.let {
			try {
				camera.parameters = parameters
				flash = flash xor true
			} catch (e: RuntimeException) {
				// ignore; there's nothing we can do
			}
		}
	}

	private fun decodeFrame(
		frameData: ByteArray?,
		frameWidth: Int,
		frameHeight: Int,
		frameOrientation: Int
	): Result? {
		frameData ?: return null
		invert = invert xor true
		return try {
			val pp = preprocessor ?: createPreprocessorAndMapping(
				frameWidth,
				frameHeight,
				frameOrientation
			)
			pp.process(frameData)
			preprocessor = pp
			zxing.decode(
				frameData,
				pp.outWidth,
				pp.outHeight,
				invert
			)
		} catch (e: RSRuntimeException) {
			prefs.forceCompat = prefs.forceCompat xor true
			// now the only option is to restart the app because
			// RenderScript.forceCompat() needs to be called before
			// RenderScript is initialized
			restartApp(this)
			null
		}
	}

	private fun createPreprocessorAndMapping(
		frameWidth: Int,
		frameHeight: Int,
		frameOrientation: Int
	): Preprocessor {
		val pp = Preprocessor(
			this,
			frameWidth,
			frameHeight,
			frameOrientation
		)
		mapping = frameToView(
			pp.outWidth,
			pp.outHeight,
			cameraView.previewRect
		)
		return pp
	}

	private fun postResult(result: Result) {
		cameraView.post {
			val rp = result.resultPoints
			val m = mapping
			if (m != null && rp != null && rp.isNotEmpty()) {
				detectorView.mark(m.map(rp))
			}
			vibrator.vibrate()
			showResult(
				this@CameraActivity,
				result,
				returnResult
			)
		}
	}

	companion object {
		private const val REQUEST_CAMERA = 1
		private const val PICK_FILE_RESULT_CODE = 1
		private const val ZOOM_MAX = "zoom_max"
		private const val ZOOM_LEVEL = "zoom_level"
	}
}

fun showResult(
	activity: Activity,
	result: Result,
	isResult: Boolean = false
) {
	val scan = Scan(result)
	if (isResult) {
		activity.setResult(Activity.RESULT_OK, getReturnIntent(result))
		activity.finish()
	} else {
		if (prefs.useHistory) {
			GlobalScope.launch {
				db.insertScan(scan)
			}
		}
		activity.startActivity(
			MainActivity.getDecodeIntent(activity, scan)
		)
	}
}

fun getReturnIntent(result: Result): Intent {
	val intent = Intent()
	intent.putExtra("SCAN_RESULT", result.text)
	intent.putExtra(
		"SCAN_RESULT_FORMAT",
		result.barcodeFormat.toString()
	)
	if (result.rawBytes?.isNotEmpty() == true) {
		intent.putExtra("SCAN_RESULT_BYTES", result.rawBytes)
	}
	result.resultMetadata?.let { metadata ->
		metadata[ResultMetadataType.UPC_EAN_EXTENSION]?.let {
			intent.putExtra(
				"SCAN_RESULT_ORIENTATION",
				it.toString()
			)
		}
		metadata[ResultMetadataType.ERROR_CORRECTION_LEVEL]?.let {
			intent.putExtra(
				"SCAN_RESULT_ERROR_CORRECTION_LEVEL",
				it.toString()
			)
		}
		metadata[ResultMetadataType.UPC_EAN_EXTENSION]?.let {
			intent.putExtra(
				"SCAN_RESULT_UPC_EAN_EXTENSION",
				it.toString()
			)
		}
		metadata[ResultMetadataType.BYTE_SEGMENTS]?.let {
			var i = 0
			@Suppress("UNCHECKED_CAST")
			for (seg in it as Iterable<ByteArray>) {
				intent.putExtra("SCAN_RESULT_BYTE_SEGMENTS_$i", seg)
				++i
			}
		}
	}
	return intent
}
