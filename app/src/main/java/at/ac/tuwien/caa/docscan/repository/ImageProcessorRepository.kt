package at.ac.tuwien.caa.docscan.repository

import androidx.room.withTransaction
import at.ac.tuwien.caa.docscan.camera.cv.thread.crop.Mapper
import at.ac.tuwien.caa.docscan.camera.cv.thread.crop.PageDetector
import at.ac.tuwien.caa.docscan.db.AppDatabase
import at.ac.tuwien.caa.docscan.db.dao.DocumentDao
import at.ac.tuwien.caa.docscan.db.dao.PageDao
import at.ac.tuwien.caa.docscan.db.model.Document
import at.ac.tuwien.caa.docscan.db.model.Page
import at.ac.tuwien.caa.docscan.db.model.boundary.SinglePageBoundary.Companion.getDefault
import at.ac.tuwien.caa.docscan.db.model.boundary.asClockwiseList
import at.ac.tuwien.caa.docscan.db.model.boundary.asPoint
import at.ac.tuwien.caa.docscan.db.model.boundary.rotateBy90
import at.ac.tuwien.caa.docscan.db.model.exif.Rotation
import at.ac.tuwien.caa.docscan.db.model.setSinglePageBoundary
import at.ac.tuwien.caa.docscan.db.model.state.PostProcessingState
import at.ac.tuwien.caa.docscan.logic.*
import at.ac.tuwien.caa.docscan.logic.applyRotation
import com.google.common.hash.Hashing
import com.google.common.io.Files.asByteSource
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.nio.file.Files

class ImageProcessorRepository(
    private val pageDao: PageDao,
    private val documentDao: DocumentDao,
    private val fileHandler: FileHandler,
    private val appDatabase: AppDatabase
) {
    // TODO: use own scope to launch work on images
    // TODO: Check how to maybe use a global scope to perform this stuff or if a work manager is necessary.

    private val scope = CoroutineScope(Dispatchers.IO)

    fun rotateFile(file: File, rotation: Rotation) {
        applyRotation(file, rotation)
    }

    fun removeRotationExif(file: File) {
        removeRotation(file)
    }

    // TODO: Define the type of detection
    // TODO: define if a single/double page detection should be performed.

    /**
     * Pre-Condition: The document is not locked.
     */
    fun spawnPageDetection(page: Page) {
        scope.launch {
            pageDao.updatePageProcessingState(page.id, PostProcessingState.PROCESSING)
            fileHandler.getFileByPage(page)?.let {
                try {
                    val result = PageDetector.findRectAndFocus(it.absolutePath)
                    if (result.points.size > 3) {
                        pageDao.getPageById(page.id)?.let { page ->
                            page.setSinglePageBoundary(result.points)
                            pageDao.insertPage(page)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            // the state is updated to draft, since it doesn't mean that the post processing is done.
            pageDao.updatePageProcessingState(page.id, PostProcessingState.DRAFT)
        }
    }

    /**
     * Pre-Condition: The document is not locked.
     */
    fun cropDocument(document: Document) {
        scope.launch {
            documentDao.getDocumentWithPages(document.id)?.let { doc ->
                pageDao.updatePageProcessingStateForDocument(
                    doc.document.id,
                    PostProcessingState.PROCESSING
                )
                // TODO: This can be performed on multiple threads to speed up the processing.
                // TODO: Perform the processing based on the numbering order.
                doc.pages.forEach { page ->
                    fileHandler.getFileByPage(page)?.let {
                        val points = (page.singlePageBoundary?.asClockwiseList()
                            ?: getDefault().asClockwiseList()).map { pointF -> pointF.asPoint() }
                        try {
                            Mapper.replaceWithMappedImage(it.absolutePath, ArrayList(points))
                        } catch (e: Exception) {
                            Timber.e("Cropping has failed!", e)
                        }
                    }
                    // TODO: The boundaries are sometimes still shown.
                    pageDao.updatePageProcessingState(page.id, PostProcessingState.DONE)
                }
                // TODO: This shouldn't be actually necessary
                pageDao.updatePageProcessingStateForDocument(
                    doc.document.id,
                    PostProcessingState.DONE
                )
            }
        }
    }

    /**
     * Pre-Condition: The document is not locked.
     */
    suspend fun rotatePages90CW(pages: List<Page>) {
        withContext(NonCancellable) {
            Timber.d("ROTATION_DOCSCAN rotatePages90CW ")
            appDatabase.withTransaction {
                pages.forEach { page ->
                    pageDao.updatePageProcessingState(page.id, PostProcessingState.PROCESSING)
//                    pageDao.updatePageProcessingState(page.id, PostProcessingState.PROCESSING)
//                    pageDao.updatePageProcessingState(page.id, PostProcessingState.PROCESSING)
//                    pageDao.updatePageProcessingState(page.id, PostProcessingState.PROCESSING)
                }
            }
            pages.forEach { page ->

                page.rotatePageBy90CW()
                Timber.d("ROTATION_DOCSCAN: new has after: ${page.fileHash}")
                // TODO: Re-check this structure
                appDatabase.withTransaction {
                    pageDao.insertPage(page)
                    pageDao.updatePageProcessingState(page.id, PostProcessingState.DRAFT)
                }
            }
        }
    }

    fun uploadDocument(document: Document) {
        // TODO: spawn upload job, check pre-elminaries before
    }

    private fun Page.rotatePageBy90CW() {
        val newRotation = rotation.rotateBy90Clockwise()
        rotation = newRotation
        val cache = fileHandler.createCacheFile(id)
        cache.safelyDelete()
        val file = fileHandler.getFileByPage(this) ?: return
        fileHandler.copyFile(file, cache)
        cache.let {
            applyRotation(it, newRotation)
            this.fileHash = it.getFileHash()
        }
        fileHandler.copyFile(cache, file)
        cache.safelyDelete()
        singlePageBoundary?.rotateBy90()
    }
}
