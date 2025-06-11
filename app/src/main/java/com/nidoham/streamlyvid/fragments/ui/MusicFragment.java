package com.nidoham.streamlyvid.fragments.ui; // Adjust package as needed

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nidoham.streamlyvid.databinding.FragmentsMusicsBinding;

public class MusicFragment extends Fragment {

    private FragmentsMusicsBinding binding; // Declare a binding variable

    public MusicFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout using View Binding
        binding = FragmentsMusicsBinding.inflate(inflater, container, false);
        return binding.getRoot(); // Return the root view of the binding
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Now you can access views from fragment_music.xml using the binding object
        // Example: Accessing the TextView we gave an ID to
        // binding.musicFragmentText.setText("Welcome to the Music Section!");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear the binding when the view is destroyed to avoid memory leaks
        binding = null;
    }
}