package com.atdialer.app.ui.contacts

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
import com.atdialer.app.databinding.FragmentDialerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class DialerFragment : Fragment() {

    private var _binding: FragmentDialerBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: ContactAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDialerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Contacts RecyclerView
        adapter = ContactAdapter(
            onDial = { contact -> vm.dial(contact.number, contact.name) },
            onDelete = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("מחיקת איש קשר")
                    .setMessage("למחוק את ${contact.name}?")
                    .setPositiveButton("מחק") { _, _ -> vm.deleteContact(contact) }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
        )
        binding.contactsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRecycler.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.contacts.collect { adapter.submitList(it) }
            }
        }

        // Manual dial
        binding.dialButton.setOnClickListener {
            val number = binding.numberInput.text.toString().trim()
            val name   = binding.nameInput.text.toString().trim().ifEmpty { number }
            if (number.isEmpty()) {
                binding.numberInput.error = "הזן מספר"
                return@setOnClickListener
            }
            vm.dial(number, name)
            binding.numberInput.setText("")
            binding.nameInput.setText("")
        }

        // Add contact
        binding.addContactButton.setOnClickListener {
            val number = binding.numberInput.text.toString().trim()
            val name   = binding.nameInput.text.toString().trim()
            if (number.isEmpty()) { binding.numberInput.error = "הזן מספר"; return@setOnClickListener }
            if (name.isEmpty())   { binding.nameInput.error   = "הזן שם";   return@setOnClickListener }
            vm.addContact(name, number)
            binding.numberInput.setText("")
            binding.nameInput.setText("")
            Toast.makeText(requireContext(), "נשמר: $name", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── RecyclerView Adapter ──────────────────────────────

class ContactAdapter(
    private val onDial: (Contact) -> Unit,
    private val onDelete: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
            override fun areContentsTheSame(a: Contact, b: Contact) = a == b
        }
    }

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val name:   TextView = view.findViewById(R.id.contact_name)
        val number: TextView = view.findViewById(R.id.contact_number)
        val dial:   View     = view.findViewById(R.id.btn_dial)
        val delete: View     = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.name.text   = c.name
        holder.number.text = c.number
        holder.dial.setOnClickListener   { onDial(c) }
        holder.delete.setOnClickListener { onDelete(c) }
    }
}
