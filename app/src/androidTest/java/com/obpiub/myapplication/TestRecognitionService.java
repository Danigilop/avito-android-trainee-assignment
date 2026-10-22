package com.obpiub.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

public class TestRecognitionService extends RecognitionService {
    @Override protected void onStartListening(Intent intent, Callback listener) {
        try {
            if (!"ru-RU".equals(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))) {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
                return;
            }
            listener.readyForSpeech(new Bundle());
            listener.beginningOfSpeech();
            listener.endOfSpeech();
            Bundle result = new Bundle();
            ArrayList<String> texts = new ArrayList<>();
            texts.add("Проверка системного распознавания");
            result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, texts);
            listener.results(result);
        } catch (RemoteException ignored) {

        }
    }
    @Override protected void onStopListening(Callback listener) {}
    @Override protected void onCancel(Callback listener) {}
}
