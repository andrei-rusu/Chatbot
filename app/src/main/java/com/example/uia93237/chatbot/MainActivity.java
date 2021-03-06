package com.example.uia93237.chatbot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.protobuf.ByteString;
import com.vdurmont.emoji.EmojiParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ai.api.AIListener;
import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.AIService;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;

import java8.util.stream.StreamSupport;


public class MainActivity extends AppCompatActivity implements AIListener {


    // Identifier of the chat instance on the Firebase DB - obtained through UUID
    private static String uniqueID = null;
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";

    // Used for connecting to a specific Google Dialogflow Agent
    private final static String accessToken = "f71f8955cc7a4003995542bb46f4f9e4";

    private final static String botName = "Agnes";
    private final static String userName = "User";

    // Used for connecting to various Google Cloud Services - OAuth credentials
    private final static String jsonAccessFile = "Chatbot-a7303d69abea.json";

    // Permissions which are requested by the app
    private final static String[] permissions = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
    };
    private final static int PERMISSION_CODE = 1;


    // Text field used for sending messages to Dialogflow
    private EditText editText;

    // Dialog which prompts for internet connection
    private Dialog dialog;

    // Boolean which determines whether TTS is being used
    private boolean isUseTTS;

    // Boolean used to switch between the 2 pictograms (send and mic) in the fab button
    private boolean flagFab = true;

    // Default TTS engine
    private TextToSpeech tts;

    // Cloud TTS engine
    private TextToSpeechClient textToSpeechClient;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private MediaPlayer mediaPlayer;

    // Firebase DB
    private DatabaseReference ref;

    // Firebase Recycler Adapter
    private FirebaseRecyclerAdapter<Message, ChatViewHolder> adapter;

    // Dialogflow service
    private AIService aiService;
    private AIDataService aiDataService;

    // Location related instances
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Map<String, String> locationMap;



    /*
    Main Activity methods
     */

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);

        // Set up view elements
        initUIElements();

        // Request: Record Audio and Location Permission
        checkPermissions();

        // Set up location related instances
        initLocationServices();

        // Set up Dialogflow service
        initDialogflowService();

        // Set up Firebase
        initFirebase();

        //Configure FirebaseRecyclerAdapter -> FirebaseUI
        initFirebaseRecyclerAdapter();

        // Set up UI listeners
        initUIListeners();

        // Set up Default TTS
        initTTS();

        // Set up Cloud TTS
        initCloudTTS();

    }


    @Override
    protected void onResume() {
        super.onResume();

        internetCheckOrClose();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {

        stopLocationUpdates();

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {

        aiService.stopListening();

        adapter.stopListening();

        textToSpeechClient.close();
        textToSpeechClient.shutdown();

        tts.stop();
        tts.shutdown();

        mediaPlayer.reset();
        mediaPlayer.release();

        super.onDestroy();
    }



    /*
    Activity methods regarding Menu Options
     */

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem checkable = menu.findItem(R.id.action_tts);
        checkable.setChecked(isUseTTS);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_tts:
                flipUseTTS(item);
                break;
            case R.id.action_exit:
                triggerAppExit();
                break;
        }
        return super.onOptionsItemSelected(item);
    }



    /*
    Init methods
     */


    private void initUIElements() {

        setContentView(R.layout.activity_main);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayShowHomeEnabled(true);
            bar.setLogo(R.mipmap.ic_launcher);
            bar.setDisplayUseLogoEnabled(true);
        }

        editText = findViewById(R.id.editText);

    }

    private void initDialogflowService() {

        // Set up Dialogflow
        final AIConfiguration config =
                new AIConfiguration(accessToken, AIConfiguration.SupportedLanguages.English, AIConfiguration.RecognitionEngine.System);

        // Used for voice request
        aiService = AIService.getService(this, config);
        aiService.setListener(this);

        // Used for text request
        aiDataService = new AIDataService(this, config);
    }


    @SuppressWarnings("Convert2MethodRef")
    private void initCloudTTS() {

        try {

            InputStream stream = this.getAssets().open(jsonAccessFile);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);

            TextToSpeechSettings textToSpeechSettings =
                    TextToSpeechSettings.newBuilder()
                            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                            .build();


            textToSpeechClient = TextToSpeechClient.create(textToSpeechSettings);


            // Select the language code and name - List can be found here: https://cloud.google.com/text-to-speech/docs/voices
            String langCode = "en-AU";
            voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(langCode)
                    .setName(langCode + "-Standard-C")
                    .build();

            // Select the type of audio file you want returned
            audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();


            // Set up media player which shall play the byte file
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnPreparedListener(mp -> mp.start());

        }
        catch (IOException e) {
            Log.e("TTSException", e.getMessage());
        }

    }

    private void initUIListeners() {

        RelativeLayout addBtn = findViewById(R.id.addBtn);
        addBtn.setOnClickListener(view -> {

            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);

            // Get message from the editor
            String message = editText.getText().toString().trim();

            // Set location related context and request extras
            AIContext locationCtx = new AIContext("location");
            locationCtx.setParameters(locationMap);
            RequestExtras requestExtras = new RequestExtras(Collections.singletonList(locationCtx), null);

            if (!message.equals("")) {

                Message chatMessage = new Message(message, userName);
                ref.child(id(this)).push().setValue(chatMessage);

                // Inititialize request to DialogFlow
                final AIRequest aiRequest = new AIRequest();
                aiRequest.setQuery(message);

                new QueryTask(requestExtras).execute(aiRequest);
            }
            else { // the else block takes care of voice input
                aiService.startListening(requestExtras);
            }

            editText.setText("");
        });


        // Configure listener for edit text in order to change the pic
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ImageView fab_img = findViewById(R.id.fab_img);
                Bitmap img = BitmapFactory.decodeResource(getResources(), R.drawable.ic_send_white_24dp);
                Bitmap img1 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mic_white_24dp);

                if (s.toString().trim().length() != 0 && flagFab) {
                    imageViewAnimatedChange(MainActivity.this, fab_img, img);
                    flagFab = false;
                } else if (s.toString().trim().length() == 0) {
                    imageViewAnimatedChange(MainActivity.this, fab_img, img1);
                    flagFab = true;
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }


    private void initTTS() {

        tts = new TextToSpeech(this, (status) -> {
            if(status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.UK);
            }
        });
    }

    private void initLocationServices() {

        // Set up location related instances
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationMap = new HashMap<>();
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    locationMap.put("latitude", String.valueOf(location.getLatitude()));
                    locationMap.put("longitude", String.valueOf(location.getLongitude()));
                }
            }
        };
    }

    private void initFirebase() {

        ref = FirebaseDatabase.getInstance().getReference();
        ref.keepSynced(true);
        setupDBCleanupListener();
    }

    private void initFirebaseRecyclerAdapter() {

        // Set up the Adapter
        FirebaseRecyclerOptions<Message> options =
                new FirebaseRecyclerOptions.Builder<Message>()
                        .setQuery(ref.child(id(this)), Message.class)
                        .build();

        adapter = new FirebaseRecyclerAdapter<Message, ChatViewHolder>(options){

            @Override
            protected void onBindViewHolder(@NonNull ChatViewHolder holder, int position, @NonNull Message model) {
                if(model.getMsgUser().equals(userName)){


                    holder.rightText.setText(model.getMsgText());

                    holder.rightText.setVisibility(View.VISIBLE);
                    holder.leftText.setVisibility(View.GONE);
                }
                else{
                    holder.leftText.setText(model.getMsgText());

                    holder.rightText.setVisibility(View.GONE);
                    holder.leftText.setVisibility(View.VISIBLE);
                }
            }

            @NonNull
            @Override
            public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.msglist, parent, false);

                return new ChatViewHolder(view);

            }
        };


        // Register the Adapter with the recyclerView

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);

                int msgCount = adapter.getItemCount();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();

                if (lastVisiblePosition == -1 || (positionStart >= (msgCount - 1)
                        && lastVisiblePosition == (positionStart - 1))) {
                    recyclerView.scrollToPosition(positionStart);
                }
            }
        });

        recyclerView.setAdapter(adapter);


        // Start the events-listening routine of the Adapter
        adapter.startListening();
    }



    /*
    Utility methods
     */

    // handles the response coming back from the Google Dialogflow engine after being analysed
    private void handleResponse(AIResponse response) {

        String reply = response.getResult().getFulfillment().getSpeech();

        if (isUseTTS) {

            if (android.os.Build.VERSION.SDK_INT >= 21) {

                // Perform the Cloud text-to-speech request on the text input with the selected voice parameters and audio type

                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(prepareForTTS(reply))
                        .build();

                SynthesizeSpeechResponse speechResp =
                        textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

                // Get the audio contents from the response
                ByteString audioContents = speechResp.getAudioContent();

                playMp3(audioContents);


//                // Use Default TTS service
//                tts.speak(prepareForTTS(reply), TextToSpeech.QUEUE_ADD, null, null); // API >= 21

            }
            else {
                // Use Default TTS service to render the text response in audio
                tts.speak(prepareForTTS(reply), TextToSpeech.QUEUE_ADD, null);

            }

        }

        /*
        Set text bubble for the Bot chat participant

        id(this) [= ID] -> returns a unique id for this app session on this phone (resets if app is reinstalled)
        ref.child(ID) [= Reference] -> gets a Reference to the DB location at the relative path named as "ID"
                                       Effectively: the current chat instance
        Reference.push() [= Child] -> creates a "child" node for Reference with a randomized name
        Child.setValue(message) -> the node is bound to a Message object
                                   Effectively: the node becomes a holder for a specific message (in this case from the Bot)
         */

        Message chatMessageBot = new Message(reply, botName);
        ref.child(id(this)).push().setValue(chatMessageBot);

    }

    private void playMp3(ByteString mp3ByteString) {

        String url = "data:audio/mp3;base64," + Base64.encodeToString(mp3ByteString.toByteArray(), Base64.DEFAULT);
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
        }
        catch(IOException e) {
            Log.e("MediaPlayerException", e.getMessage());
        }
    }

    private void flipUseTTS(MenuItem item) {

        isUseTTS ^= true;
        item.setChecked(isUseTTS);
    }

    // Requests permissions if not already granted
    private void checkPermissions() {

        String[] toRequest =
                StreamSupport
                        .stream(Arrays.asList(permissions))
                        .filter(perm -> checkCallingOrSelfPermission(perm) != PackageManager.PERMISSION_GRANTED)
                        .toArray(String[]::new);

        if (toRequest.length != 0) {
            ActivityCompat.requestPermissions(this, toRequest, PERMISSION_CODE);
        }
    }

    // Shows required connection dialog if the internet is unavailable
    private void internetCheckOrClose() {

        if (!isInternetAvailable()) {
            showConnectionDialog();
        }
    }

    // Pings google.com to check for internet connection
    private boolean isInternetAvailable() {
        InetAddress inetAddress = null;
        try {
            Future<InetAddress> future = Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    return InetAddress.getByName("google.com");
                } catch (UnknownHostException e) {
                    return null;
                }
            });

            inetAddress = future.get(3, TimeUnit.SECONDS);
            future.cancel(true);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.d("CONNECTION PROBLEM", "Internet not available!");
        }

        return inetAddress != null && !inetAddress.toString().equals("");
    }

    // Shows dialog which prompts user to enable internet connection
    private void showConnectionDialog() {
        if (dialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Agnes needs Internet Connection!")
                    .setCancelable(false)
                    .setPositiveButton("Connect to Internet", (dialog, id) -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)))
                    .setNegativeButton("Quit", (dialog, id) -> this.finish());

            dialog = builder.show();
        }
        else if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    // Requests location updates from the FusedLocationClient
    private void startLocationUpdates() {

        try {
            mFusedLocationClient.requestLocationUpdates(LocationRequest.create(), mLocationCallback, null);
        } catch (SecurityException e) {
            Log.d("LOCATION UNAVAILABLE", "Location service is not available!");
        }
    }

    // Stops the location updates
    private void stopLocationUpdates() {

        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    // Listener which cleans-up database entries older than 1 day
    private void setupDBCleanupListener() {
        long cutoff = new Date().getTime() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
        Query oldItems = ref.child(id(this)).orderByChild("timestamp").endAt(cutoff);
        oldItems.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot itemSnapshot: snapshot.getChildren()) {
                    itemSnapshot.getRef().removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                throw databaseError.toException();
            }
        });
    }


    private void triggerAppExit() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle("Exit")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    finish();
                    System.exit(0);
                }).setNegativeButton("No", null).show();
    }


    /*
    Static utility methods
     */

    // Prepare the TTS input by returning only the first line of the reply, removing all Emojis in the process
    private static String prepareForTTS(String s) {

        return EmojiParser.removeAllEmojis(s.split("\\r?\\n")[0]);
    }


    // Method used to assign an ID per installation of App. This will uniquely identify a chat instance
    private static synchronized String id(Context context) {

        if (uniqueID == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    PREF_UNIQUE_ID, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);
            if (uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PREF_UNIQUE_ID, uniqueID);
                editor.apply();
            }
        }
        return uniqueID;
    }


    // Method used to change the fab button pictograms with animation
    private static void imageViewAnimatedChange(Context c, final ImageView v, final Bitmap new_image) {

        final Animation anim_out = AnimationUtils.loadAnimation(c,
                android.R.anim.fade_out);
        final Animation anim_in = AnimationUtils.loadAnimation(c,
                android.R.anim.fade_in);
        anim_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationRepeat(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                v.setImageBitmap(new_image);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                    }
                });
                v.startAnimation(anim_in);
            }
        });
        v.startAnimation(anim_out);
    }



    /*
    AIListener interface implementation
     */

    @Override
    public void onResult(AIResponse response) {

        // set the text bubble for the user query
        String rawMessage = response.getResult().getResolvedQuery();
        String capitalisedMessage = rawMessage.substring(0, 1).toUpperCase() + rawMessage.substring(1);
        Message chatMessageUser = new Message(capitalisedMessage, userName);
        ref.child(id(this)).push().setValue(chatMessageUser);

        // handle the response, including the creation of the text bubble for the bot
        handleResponse(response);
    }

    @Override
    public void onError(AIError error) {

    }

    @Override
    public void onAudioLevel(float level) {

    }

    @Override
    public void onListeningStarted() {

    }

    @Override
    public void onListeningCanceled() {

    }

    @Override
    public void onListeningFinished() {

    }



    // Code that executes after a permission is granted
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_CODE) {

            for (int i = 0; i < permissions.length; i++) {

                String permission = permissions[i];
                int grantResult = grantResults[i];

                switch(permission) {
                    case Manifest.permission.ACCESS_FINE_LOCATION:
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this,"GPS permission granted", Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(this,"GPS permission denied", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case Manifest.permission.RECORD_AUDIO:
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this,"Audio recording permission granted", Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(this,"Audio recording permission denied", Toast.LENGTH_LONG).show();
                        }
                }
            }

        }
    }


    /*
     AsyncTask that will complete the query to the AIDataService, calling handleResponse() on the resulting AIResponse
      */

    @SuppressLint("StaticFieldLeak")
    private class QueryTask extends AsyncTask <AIRequest, Void, AIResponse> {

        private RequestExtras extras;

        private QueryTask(RequestExtras extras) {
            this.extras = extras;
        }

        @Override
        protected AIResponse doInBackground(AIRequest... aiRequests) {

            final AIRequest req = aiRequests[0];
            try {
                return aiDataService.request(req, extras);
            } catch (AIServiceException e) {
                Log.d("AIServiceException", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(AIResponse response) {
            if (response != null) {
                handleResponse(response);
            }
        }
    }

}
