package com.atdialer.app.ui.log

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.atdialer.app.MainViewModel
import com.atdialer.app.R
import com.atdialer.app.data.CallLogEntry
import com.atdialer.app.databinding.FragmentLogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LogAdapter(onDelete = { vm.deleteLogEntry(it) })
        binding.logRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.logRecycler.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.callLogs.collect { logs ->
                    adapter.submitList(logs)
                    updateStats(logs)
                }
            }
        }

        binding.clearLogButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("ניקוי לוג")
                .setMessage("למחוק את כל הרשומות?")
                .setPositiveButton("מחק") { _, _ -> vm.clearLog() }
                .setNegativeButton("ביטול", null)
                .show()
        }

        binding.exportButton.setOnClickListener { exportLog() }
    }

    private fun updateStats(logs: List<CallLogEntry>) {
        val total    = logs.size
        val answered = logs.count { it.outcome == "ANSWERED" }
        val totalSec = logs.sumOf { it.durationSeconds }
        val avgSec   = if (answered > 0) totalSec / answered else 0

        binding.statTotal.text    = "סה\"כ שיחות: $total"
        binding.statAnswered.text = "נענו: $answered"
        binding.statNoAnswer.text = "לא נענו: ${logs.count { it.outcome == "NO_ANSWER" }}"
        binding.statBusy.text     = "תפוס: ${logs.count { it.outcome == "BUSY" }}"
        binding.statAvgDur.text   = "משך ממוצע: ${formatDuration(avgSec)}"
        binding.statTotalDur.text = "סה\"כ זמן: ${formatDuration(totalSec)}"
    }

    private fun exportLog() {
        val logs = adapter.currentList
        val sb = StringBuilder("שם,מספר,תאריך,שעה,משך,תוצאה,מזהה תזמון\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy,HH:mm:ss", Locale.getDefault())
        logs.forEach { log ->
            sb.append("${log.contactName},${log.number},${sdf.format(Date(log.startTime))},")
            sb.append("${formatDuration(log.durationSeconds)},${translateOutcome(log.outcome)},${log.scheduleId ?: ""}\n")
        }
        // Share as text
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AT Dialer - Call Log")
        }
        startActivity(android.content.Intent.createChooser(intent, "ייצוא לוג"))
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60; val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun translateOutcome(outcome: String) = when(outcome) {
        "ANSWERED"  -> "נענה"
        "NO_ANSWER" -> "לא נענה"
        "BUSY"      -> "תפוס"
        "FAILED"    -> "נכשל"
        "ONGOING"   -> "פעיל"
        else        -> outcome
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Adapter ───────────────────────────────────────────

class LogAdapter(
    private val onDelete: (CallLogEntry) -> Unit
) : ListAdapter<CallLogEntry, LogAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CallLogEntry>() {
            override fun areItemsTheSame(a: CallLogEntry, b: CallLogEntry) = a.id == b.id
            override fun areContentsTheSame(a: CallLogEntry, b: CallLogEntry) = a == b
        }
        val SDF = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name:    TextView = view.findViewById(R.id.log_name)
        val number:  TextView = view.findViewById(R.id.log_number)
        val time:    TextView = view.findViewById(R.id.log_time)
        val outcome: TextView = view.findViewById(R.id.log_outcome)
        val dur:     TextView = view.findViewById(R.id.log_duration)
        val delete:  View     = view.findViewById(R.id.log_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        holder.name.text    = e.contactName
        holder.number.text  = e.number
        holder.time.text    = SDF.format(Date(e.startTime))
        val dur = e.durationSeconds; val m = dur/60; val s = dur%60
        holder.dur.text     = "%d:%02d".format(m, s)
        holder.outcome.text = when(e.outcome) {
            "ANSWERED"  -> "✅ נענה"
            "NO_ANSWER" -> "❌ לא נענה"
            "BUSY"      -> "🔴 תפוס"
            "FAILED"    -> "⚠️ נכשל"
            "ONGOING"   -> "📞 פעיל"
            else        -> e.outcome
        }
        holder.delete.setOnClickListener { onDelete(e) }
    }
}
