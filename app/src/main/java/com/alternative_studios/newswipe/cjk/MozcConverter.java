package com.alternative_studios.newswipe.cjk;

import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWord;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An imported, version-pinned Mozc runtime. NewSwipe owns the preedit and candidate selection. */
public final class MozcConverter implements Converter {
    static final String LIB = "libmozc.so", DATA = "mozc.data";
    static final String LIB_HASH = "e24b8311e7ffce3ebc433079f2c51e56d749d228805225685b01df032de05e57";
    static final String DATA_HASH = "01bdae043c548f05ae6a35c156612fc470437c952b73dc6fc57f0f05a4cc0779";
    private static MozcConverter engine;
    private final long session;

    // Only called for files validated by CjkPack under its install/load lock.
    static synchronized MozcConverter open(File pack, File filesDir) throws IOException {
        if (engine != null) return engine;
        File profile = new File(filesDir, "mozc-profile");
        if (!profile.isDirectory() && !profile.mkdirs()) throw new IOException("Mozc profile");
        try {
            System.load(new File(pack, LIB).getAbsolutePath());
            if (!MozcJni.initialize() || !MozcJni.onPostLoad(profile.getPath(), new File(pack, DATA).getPath()))
                throw new IOException("Mozc initialization");
            MozcConverter instance = new MozcConverter();
            if (instance.convert("にほんご", 80).stream().noneMatch(c -> c.text.equals("日本語")))
                throw new IOException("Mozc dictionary check failed (" + MozcJni.getDataVersion() + "): " + instance.convert("にほんご", 8));
            engine = instance;
            return instance;
        } catch (LinkageError | RuntimeException e) {
            throw new IOException("Mozc (Android arm64-v8a): " + e.getMessage(), e);
        }
    }

    private MozcConverter() throws IOException {
        session = eval(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION)).getId();
        if (session == 0) throw new IOException("Mozc session");
        eval(input(Input.CommandType.SET_REQUEST).setRequest(Request.newBuilder()
                .setMixedConversion(true).setAutoPartialSuggestion(false).setZeroQuerySuggestion(false)));
        command(SessionCommand.CommandType.TURN_ON_IME);
    }

    private Input.Builder input(Input.CommandType type) {
        return Input.newBuilder().setType(type).setId(session);
    }

    private static Output eval(Input.Builder input) throws IOException {
        byte[] bytes = Command.newBuilder().setInput(input).build().toByteArray();
        Output result = Command.parseFrom(MozcJni.evalCommand(bytes)).getOutput();
        if (result.getErrorCode() != Output.ErrorCode.SESSION_SUCCESS)
            throw new IOException("Mozc " + input.getType());
        return result;
    }

    private Output command(SessionCommand.CommandType type) throws IOException {
        return eval(input(Input.CommandType.SEND_COMMAND).setCommand(SessionCommand.newBuilder().setType(type)));
    }

    @Override
    public synchronized List<Candidate> convert(String reading, int max) {
        Map<String, Candidate> result = new LinkedHashMap<>();
        try {
            command(SessionCommand.CommandType.RESET_CONTEXT);
            Output out = eval(input(Input.CommandType.SEND_KEY).setKey(KeyEvent.newBuilder()
                    .setKeyString(reading).setInputStyle(KeyEvent.InputStyle.AS_IS)));
            for (CandidateWord word : out.getAllCandidateWords().getCandidatesList()) {
                if (result.size() >= max) break;
                String text = word.getValue();
                // auto_partial_suggestion is off. content_key may omit inflectional endings.
                if (!text.isEmpty()) result.putIfAbsent(text, new Candidate(text, reading.length()));
            }
        } catch (IOException e) {
            android.util.Log.w("NewSwipeMozc", "Candidate request failed", e);
        }
        if (result.size() < max) result.putIfAbsent(reading, new Candidate(reading, reading.length()));
        return new ArrayList<>(result.values());
    }
}
