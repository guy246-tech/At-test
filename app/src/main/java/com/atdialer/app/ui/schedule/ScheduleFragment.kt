package com.atdialer.app.ui.schedule

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.*
import com.atdialer.app.MainViewModel
import com.atdialer.app.R
import com.atdialer.app.data.Contact
import com.atdialer.app.data.Schedule
import com.atdialer.app.databinding.FragmentScheduleBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: ScheduleAdapter
    private var contactList: List<Contact> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ScheduleAdapter(
            onToggle = { schedule, on -> vm.toggleSchedule(schedule, on) },
            onDelete = { schedule ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("מחיקת תזמון")
                    .setMessage("למחוק תזמון עבור ${schedule.contactName}?")
                    .setPositiveButton("מחק") { _, _ -> vm.deleteSchedule(schedule) }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
        )
        binding.scheduleRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.scheduleRecycler.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.schedules.collect { adapter.submitList(it) }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.contacts.collect { contactList = it }
            }
        }

        binding.addScheduleButton.setOnClickListener { showAddScheduleDialog() }
    }

    private fun showAddScheduleDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_schedule, null)
        val contactSpinner = dialogView.findViewById<Spinner>(R.id.spinner_contact)
        val timeButton     = dialogView.findViewById<Button>(R.id.btn_pick_time)
        val chipGroup      = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_days)
        val numberInput    = dialogView.findViewById<EditText>(R.id.et_manual_number)
        val nameInput      = dialogView.findViewById<EditText>(R.id.et_manual_name)

        var selectedHour   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var selectedMinute = Calendar.getInstance().get(Calendar.MINUTE)
        timeButton.text    = "%02d:%02d".format(selectedHour, selectedMinute)

        // Populate contact spinner
        val spinnerItems = mutableListOf("הזן מספר ידנית") + contactList.map { "${it.name} — ${it.number}" }
        contactSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, spinnerItems)

        timeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                selectedHour = h; selectedMinute = m
                timeButton.text = "%02d:%02d".format(h, m)
            }, selectedHour, selectedMinute, true).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("תזמון חיוג")
            .setView(dialogView)
            .setPositiveButton("שמור") { _, _ ->
                val selIdx  = contactSpinner.selectedItemPosition
                val number: String
                val name: String

                if (selIdx == 0) {
                    // manual
                    number = numberInput.text.toString().trim()
                    name   = nameInput.text.toString().trim().ifEmpty { number }
                    if (number.isEmpty()) {
                        Toast.makeText(requireContext(), "הזן מספר", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                } else {
                    val contact = contactList[selIdx - 1]
                    number = contact.number
                    name   = contact.name
                }

                // Collect selected days
                val days = mutableListOf<Int>()
                for (i in 0 until chipGroup.childCount) {
                    val chip = chipGroup.getChildAt(i) as? Chip
                    if (chip?.isChecked == true) chip.tag?.toString()?.toIntOrNull()?.let { days.add(it) }
                }

                val schedule = Schedule(
                    contactId   = if (selIdx == 0) -1 else contactList[selIdx - 1].id,
                    contactName = name,
                    number      = number,
                    hour        = selectedHour,
                    minute      = selectedMinute,
                    repeatDays  = days.joinToString(",")
                )
                vm.addSchedule(schedule)
                Toast.makeText(requireContext(), "תזמון נשמר", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Adapter ───────────────────────────────────────────

class ScheduleAdapter(
    private val onToggle: (Schedule, Boolean) -> Unit,
    private val onDelete: (Schedule) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(a: Schedule, b: Schedule) = a.id == b.id
            override fun areContentsTheSame(a: Schedule, b: Schedule) = a == b
        }
val DAY_NAMES = mapOf(1 to "ב", 2 to "ג", 3 to "ד", 4 to "ה", 5 to "ו", 6 to "ש", 7 to "א")    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name:    TextView = view.findViewById(R.id.sched_name)
        val time:    TextView = view.findViewById(R.id.sched_time)
        val days:    TextView = view.findViewById(R.id.sched_days)
        val toggle:  Switch   = view.findViewById(R.id.sched_toggle)
        val delete:  View     = view.findViewById(R.id.sched_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        holder.name.text   = "${s.contactName}\n${s.number}"
        holder.time.text   = "%02d:%02d".format(s.hour, s.minute)
        holder.days.text   = if (s.repeatDays.isEmpty()) "חד פעמי"
            else s.repeatDays.split(",").mapNotNull { DAY_NAMES[it.trim().toIntOrNull()] }.joinToString(" ")
        holder.toggle.isChecked = s.isEnabled
        holder.toggle.setOnCheckedChangeListener { _, on -> onToggle(s, on) }
        holder.delete.setOnClickListener { onDelete(s) }
    }
}
