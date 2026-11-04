package www.jabin24.aitally.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import www.jabin24.aitally.data.crypto.CryptoManager
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtil {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    /**
     * Exports a customer's ledger statement as a styled A4 PDF to local drive and returns the File.
     */
    fun exportCustomerStatementPdf(
        context: Context,
        shopName: String,
        customer: CustomerEntity,
        transactions: List<TransactionEntity>
    ): Result<File> {
        return try {
            val document = PdfDocument()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())
            val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val generatedDateStr = dateFormat.format(Date())

            val cleanShopName = shopName.ifBlank { "TallyKhata Digital Store" }
            val cleanCustomerName = customer.name.ifBlank { "Customer" }
            val sanitizedCustName = cleanCustomerName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val fileName = "Statement_${sanitizedCustName}_${System.currentTimeMillis()}.pdf"

            val totalCredit = transactions
                .filter { it.type == TransactionEntity.TYPE_CREDIT_GIVEN }
                .sumOf { it.amount }
            val totalDebit = transactions
                .filter { it.type == TransactionEntity.TYPE_DEBIT_RECEIVED }
                .sumOf { it.amount }
            val netBalance = customer.totalDue

            // Paints
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                color = Color.DKGRAY
            }

            val boldPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.BLACK
            }

            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.WHITE
            }

            val headerBgPaint = Paint().apply {
                color = Color.rgb(27, 94, 32) // Tally Primary Green
                style = Paint.Style.FILL
            }

            val cardBgPaint = Paint().apply {
                color = Color.rgb(245, 247, 248)
                style = Paint.Style.FILL
            }

            val borderPaint = Paint().apply {
                color = Color.rgb(218, 224, 228)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val tableHeaderPaint = Paint().apply {
                color = Color.rgb(235, 240, 236)
                style = Paint.Style.FILL
            }

            val rowAltPaint = Paint().apply {
                color = Color.rgb(250, 250, 250)
                style = Paint.Style.FILL
            }

            val greenPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.rgb(46, 125, 50)
            }

            val redPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.rgb(198, 40, 40)
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            fun drawPageHeader(isFirstPage: Boolean): Float {
                var currentY = 0f
                // Top Header Banner
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 70f, headerBgPaint)
                canvas.drawText("TallyKhata • কাস্টমার হিসাব খাতা", MARGIN, 34f, titlePaint)

                val subPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 10f
                    color = Color.rgb(200, 230, 201)
                }
                canvas.drawText("Customer Ledger Statement & Invoice", MARGIN, 52f, subPaint)

                // Date in header right
                val rightDatePaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9f
                    color = Color.WHITE
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Date: $generatedDateStr", PAGE_WIDTH - MARGIN, 42f, rightDatePaint)
                canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN, 56f, rightDatePaint)

                currentY = 86f

                if (isFirstPage) {
                    // Store & Customer Information Boxes
                    val boxWidth = (PAGE_WIDTH - (MARGIN * 2) - 16f) / 2f
                    val boxHeight = 74f

                    // Store box (Left)
                    canvas.drawRoundRect(MARGIN, currentY, MARGIN + boxWidth, currentY + boxHeight, 8f, 8f, cardBgPaint)
                    canvas.drawRoundRect(MARGIN, currentY, MARGIN + boxWidth, currentY + boxHeight, 8f, 8f, borderPaint)

                    val labelPaint = Paint().apply {
                        isAntiAlias = true
                        textSize = 9f
                        typeface = Typeface.DEFAULT_BOLD
                        color = Color.rgb(100, 116, 139)
                    }
                    canvas.drawText("STORE / হিসাবের মালিক:", MARGIN + 10f, currentY + 18f, labelPaint)
                    canvas.drawText(cleanShopName, MARGIN + 10f, currentY + 36f, boldPaint)
                    canvas.drawText("Phone: ${customer.phone.ifBlank { "Registered Store" }}", MARGIN + 10f, currentY + 52f, textPaint)
                    canvas.drawText("Status: Verified Ledger", MARGIN + 10f, currentY + 66f, textPaint)

                    // Customer box (Right)
                    val rightBoxX = MARGIN + boxWidth + 16f
                    canvas.drawRoundRect(rightBoxX, currentY, rightBoxX + boxWidth, currentY + boxHeight, 8f, 8f, cardBgPaint)
                    canvas.drawRoundRect(rightBoxX, currentY, rightBoxX + boxWidth, currentY + boxHeight, 8f, 8f, borderPaint)

                    canvas.drawText("CUSTOMER / গ্রাহকের তথ্য:", rightBoxX + 10f, currentY + 18f, labelPaint)
                    canvas.drawText(cleanCustomerName, rightBoxX + 10f, currentY + 36f, boldPaint)
                    canvas.drawText("Phone: ${customer.phone.ifBlank { "N/A" }}", rightBoxX + 10f, currentY + 52f, textPaint)
                    val statusText = if (netBalance > 0) "Current Due: ৳${String.format("%.2f", netBalance)}" else "Current Balance: Paid"
                    canvas.drawText(statusText, rightBoxX + 10f, currentY + 66f, if (netBalance > 0) redPaint else greenPaint)

                    currentY += boxHeight + 16f
                }

                // Table Header
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, tableHeaderPaint)
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, borderPaint)

                val thPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = Color.rgb(30, 41, 59)
                }

                canvas.drawText("SL", MARGIN + 6f, currentY + 15f, thPaint)
                canvas.drawText("DATE", MARGIN + 32f, currentY + 15f, thPaint)
                canvas.drawText("TRANSACTION TYPE", MARGIN + 110f, currentY + 15f, thPaint)
                canvas.drawText("MEMO / DETAILS", MARGIN + 230f, currentY + 15f, thPaint)

                val thRightPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = Color.rgb(30, 41, 59)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("AMOUNT (৳)", PAGE_WIDTH - MARGIN - 10f, currentY + 15f, thRightPaint)

                currentY += 22f
                return currentY
            }

            var currentY = drawPageHeader(isFirstPage = true)
            val rowHeight = 22f

            val sortedTxs = transactions.sortedBy { it.dateMillis }

            for ((index, tx) in sortedTxs.withIndex()) {
                // Check if page overflow
                if (currentY + rowHeight > PAGE_HEIGHT - 130f) {
                    // Draw footer on current page
                    drawFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawPageHeader(isFirstPage = false)
                }

                val isCredit = tx.type == TransactionEntity.TYPE_CREDIT_GIVEN
                val rowBg = if (index % 2 == 1) rowAltPaint else null
                if (rowBg != null) {
                    canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, rowBg)
                }
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, borderPaint)

                val dateStr = dateOnlyFormat.format(Date(tx.dateMillis))
                val typeStr = if (isCredit) "বাকি দেওয়া (Credit)" else "টাকা পাওয়া (Payment)"
                val rawMemo = CryptoManager.decrypt(tx.encryptedDetails)
                val memoStr = rawMemo.ifBlank { if (tx.receiptNumber.isNotBlank()) "Receipt #${tx.receiptNumber}" else "-" }
                val truncatedMemo = if (memoStr.length > 28) memoStr.take(25) + "..." else memoStr

                canvas.drawText("${index + 1}", MARGIN + 6f, currentY + 15f, textPaint)
                canvas.drawText(dateStr, MARGIN + 32f, currentY + 15f, textPaint)

                val typePaint = if (isCredit) redPaint else greenPaint
                canvas.drawText(typeStr, MARGIN + 110f, currentY + 15f, typePaint)

                canvas.drawText(truncatedMemo, MARGIN + 230f, currentY + 15f, textPaint)

                val amountRightPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    color = if (isCredit) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
                    textAlign = Paint.Align.RIGHT
                }
                val prefix = if (isCredit) "+ " else "- "
                canvas.drawText("$prefix৳${String.format("%.2f", tx.amount)}", PAGE_WIDTH - MARGIN - 10f, currentY + 15f, amountRightPaint)

                currentY += rowHeight
            }

            // Summary Totals Box
            currentY += 12f
            if (currentY + 80f > PAGE_HEIGHT - 60f) {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = 40f
            }

            val summaryWidth = 260f
            val summaryX = PAGE_WIDTH - MARGIN - summaryWidth
            val summaryHeight = 70f

            canvas.drawRoundRect(summaryX, currentY, summaryX + summaryWidth, currentY + summaryHeight, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(summaryX, currentY, summaryX + summaryWidth, currentY + summaryHeight, 8f, 8f, borderPaint)

            canvas.drawText("মোট বাকি (Total Given):", summaryX + 12f, currentY + 20f, textPaint)
            val alignRight = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("৳${String.format("%.2f", totalCredit)}", summaryX + summaryWidth - 12f, currentY + 20f, alignRight)

            canvas.drawText("মোট জমা (Total Received):", summaryX + 12f, currentY + 38f, textPaint)
            canvas.drawText("৳${String.format("%.2f", totalDebit)}", summaryX + summaryWidth - 12f, currentY + 38f, alignRight)

            // Net balance line
            val dueColor = if (netBalance > 0) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
            val netPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                color = dueColor
            }
            val netRight = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                color = dueColor
                textAlign = Paint.Align.RIGHT
            }
            val balanceLabel = if (netBalance >= 0) "মোট বাকি (Net Due):" else "অগ্রিম জমা (Advance):"
            canvas.drawText(balanceLabel, summaryX + 12f, currentY + 58f, netPaint)
            canvas.drawText("৳${String.format("%.2f", Math.abs(netBalance))}", summaryX + summaryWidth - 12f, currentY + 58f, netRight)

            // Draw final page footer
            drawFooter(canvas, pageNumber)
            document.finishPage(page)

            // Save to internal/external storage
            val localDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!localDir.exists()) localDir.mkdirs()
            val targetFile = File(localDir, fileName)
            FileOutputStream(targetFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            // Also copy to public Downloads folder for user to easily see in local drive
            copyToPublicDownloads(context, targetFile, fileName)

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports full store customer ledger summary PDF to local drive.
     */
    fun exportFullStoreLedgerPdf(
        context: Context,
        shopName: String,
        customers: List<CustomerEntity>,
        allTransactions: List<TransactionEntity>
    ): Result<File> {
        return try {
            val document = PdfDocument()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())
            val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val generatedDateStr = dateFormat.format(Date())

            val cleanShopName = shopName.ifBlank { "TallyKhata Store" }
            val fileName = "Full_Ledger_${cleanShopName.replace(Regex("[^a-zA-Z0-9_]"), "_")}_${System.currentTimeMillis()}.pdf"

            val totalReceivable = customers.filter { it.totalDue > 0 }.sumOf { it.totalDue }
            val totalPayable = customers.filter { it.totalDue < 0 }.sumOf { -it.totalDue }

            // Paints
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 9.5f
                color = Color.DKGRAY
            }
            val boldPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.BLACK
            }
            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.WHITE
            }
            val headerBgPaint = Paint().apply {
                color = Color.rgb(27, 94, 32)
                style = Paint.Style.FILL
            }
            val cardBgPaint = Paint().apply {
                color = Color.rgb(245, 247, 248)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(218, 224, 228)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val tableHeaderPaint = Paint().apply {
                color = Color.rgb(235, 240, 236)
                style = Paint.Style.FILL
            }
            val rowAltPaint = Paint().apply {
                color = Color.rgb(250, 250, 250)
                style = Paint.Style.FILL
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            fun drawHeader(isFirstPage: Boolean): Float {
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 70f, headerBgPaint)
                canvas.drawText("TallyKhata • সম্পূর্ণ হিসাব খাতা রিপোর্ট", MARGIN, 34f, titlePaint)

                val subPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 10f
                    color = Color.rgb(200, 230, 201)
                }
                canvas.drawText("Full Store Customers & Balance Ledger Report", MARGIN, 52f, subPaint)

                val rightDatePaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9f
                    color = Color.WHITE
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Generated: $generatedDateStr", PAGE_WIDTH - MARGIN, 42f, rightDatePaint)
                canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN, 56f, rightDatePaint)

                var currentY = 86f
                if (isFirstPage) {
                    // Summary Banner Box
                    val boxWidth = PAGE_WIDTH - (MARGIN * 2)
                    val boxHeight = 54f
                    canvas.drawRoundRect(MARGIN, currentY, MARGIN + boxWidth, currentY + boxHeight, 8f, 8f, cardBgPaint)
                    canvas.drawRoundRect(MARGIN, currentY, MARGIN + boxWidth, currentY + boxHeight, 8f, 8f, borderPaint)

                    val colWidth = boxWidth / 3f
                    // Col 1: Store Name
                    canvas.drawText("STORE NAME:", MARGIN + 12f, currentY + 18f, textPaint)
                    canvas.drawText(cleanShopName, MARGIN + 12f, currentY + 36f, boldPaint)

                    // Col 2: Total Customers
                    canvas.drawText("TOTAL CUSTOMERS:", MARGIN + colWidth + 12f, currentY + 18f, textPaint)
                    canvas.drawText("${customers.size} Clients", MARGIN + colWidth + 12f, currentY + 36f, boldPaint)

                    // Col 3: Total Due Receivable
                    val redDuePaint = Paint().apply {
                        isAntiAlias = true
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        color = Color.rgb(198, 40, 40)
                    }
                    canvas.drawText("TOTAL RECEIVABLE (পাবেন):", MARGIN + (colWidth * 2) + 12f, currentY + 18f, textPaint)
                    canvas.drawText("৳${String.format("%.2f", totalReceivable)}", MARGIN + (colWidth * 2) + 12f, currentY + 36f, redDuePaint)

                    currentY += boxHeight + 16f
                }

                // Table Header
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, tableHeaderPaint)
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f, borderPaint)

                val thPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = Color.rgb(30, 41, 59)
                }

                canvas.drawText("SL", MARGIN + 6f, currentY + 15f, thPaint)
                canvas.drawText("CUSTOMER NAME", MARGIN + 32f, currentY + 15f, thPaint)
                canvas.drawText("MOBILE NUMBER", MARGIN + 210f, currentY + 15f, thPaint)
                canvas.drawText("STATUS", MARGIN + 330f, currentY + 15f, thPaint)

                val thRightPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = Color.rgb(30, 41, 59)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("BALANCE (৳)", PAGE_WIDTH - MARGIN - 10f, currentY + 15f, thRightPaint)

                currentY += 22f
                return currentY
            }

            var currentY = drawHeader(isFirstPage = true)
            val rowHeight = 22f

            for ((index, cust) in customers.withIndex()) {
                if (currentY + rowHeight > PAGE_HEIGHT - 60f) {
                    drawFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawHeader(isFirstPage = false)
                }

                val rowBg = if (index % 2 == 1) rowAltPaint else null
                if (rowBg != null) {
                    canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, rowBg)
                }
                canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, borderPaint)

                val due = cust.totalDue
                val dueColor = when {
                    due > 0 -> Color.rgb(198, 40, 40)
                    due < 0 -> Color.rgb(46, 125, 50)
                    else -> Color.GRAY
                }
                val dueStatus = when {
                    due > 0 -> "বাকি (Due)"
                    due < 0 -> "অগ্রিম (Advance)"
                    else -> "পরিশোধিত"
                }

                canvas.drawText("${index + 1}", MARGIN + 6f, currentY + 15f, textPaint)
                canvas.drawText(cust.name.take(24), MARGIN + 32f, currentY + 15f, boldPaint)
                canvas.drawText(cust.phone.ifBlank { "N/A" }, MARGIN + 210f, currentY + 15f, textPaint)

                val statusPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 9.5f
                    color = dueColor
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText(dueStatus, MARGIN + 330f, currentY + 15f, statusPaint)

                val balRight = Paint().apply {
                    isAntiAlias = true
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    color = dueColor
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("৳${String.format("%.2f", Math.abs(due))}", PAGE_WIDTH - MARGIN - 10f, currentY + 15f, balRight)

                currentY += rowHeight
            }

            drawFooter(canvas, pageNumber)
            document.finishPage(page)

            val localDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!localDir.exists()) localDir.mkdirs()
            val targetFile = File(localDir, fileName)
            FileOutputStream(targetFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            copyToPublicDownloads(context, targetFile, fileName)

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun drawFooter(canvas: android.graphics.Canvas, pageNum: Int) {
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 8.5f
            color = Color.rgb(148, 163, 184)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 32f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 32f, linePaint)
        canvas.drawText("TallyKhata Digital Ledger (www.jabin24.com) • Generated Offline & Secure", MARGIN, PAGE_HEIGHT - 18f, footerPaint)

        val rightFooterPaint = Paint().apply {
            isAntiAlias = true
            textSize = 8.5f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 18f, rightFooterPaint)
    }

    /**
     * Copies the generated PDF into the public Downloads directory so the user can easily see it in Local Drive / Files app.
     */
    private fun copyToPublicDownloads(context: Context, sourceFile: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TallyKhata")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val tallyFolder = File(publicDownloads, "TallyKhata")
                if (!tallyFolder.exists()) tallyFolder.mkdirs()
                val destFile = File(tallyFolder, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { out ->
                        input.copyTo(out)
                    }
                }
            }
        } catch (e: Exception) {
            // Non-critical; internal storage copy is always available
        }
    }

    /**
     * Opens or shares the exported PDF with system chooser (PDF Viewer, Google Drive, WhatsApp, Print).
     */
    fun openOrSharePdf(context: Context, file: File, title: String = "TallyKhata PDF Statement") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}
