/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import edu.smu.tspell.wordnet.*;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.util.*;

/**
 *
 * @author Alessandro
 */
public class WordSenseDisambiguation {

    static String contextPath = "[PATH_TO_SRC]";
    static String pathToStopWords = contextPath + "[STOP_WORDS_FILE]";
    static String pathToStemmerFile = contextPath + "[INPUT_FILE_NAME]";
    static String pathToOutputFile = contextPath + "[LOG_OUTPUT_FILE_NAME]";
    static String pathToWNDict = "[PATH_TO_WN_DICT]";
    static WordNetDatabase database = WordNetDatabase.getFileInstance();
    static ArrayList<String> inputSentences = new ArrayList<String>();
    static ArrayList<String> inputWords = new ArrayList<String>();
    static HashMap<String, String> inputSentencesMap = new HashMap<String, String>();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.setProperty("wordnet.database.dir", pathToWNDict);
        File fileStopWords = new File(pathToStopWords);
        File log = new File(pathToOutputFile);
        initInput();
        try {
            ArrayList<String> stopWords = IOStopWords(fileStopWords);
            String line = null;
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log)));
            runProcess("pwd");
            runProcess("javac  -classpath src/stemmer Stemmer.java");
            for(Iterator<String> it = (inputSentencesMap.keySet()).iterator(); it.hasNext();) {
                String sentence = it.next();
                String word = inputSentencesMap.get(sentence);
                ArrayList<String> filteredWords = filterStopWords(sentence, stopWords);
                Synset[] synsets = database.getSynsets(word);
                Synset bestSense = leskAlgorithm(synsets, sentence, stopWords);
                out.write("sentence: [" + sentence + "]\nword: [" + word + "]\nbest sense is [" + bestSense.getWordForms()[0] + "] => (" + bestSense.getDefinition() + ")\n*\n");
            }
            out.close();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Legge le stopwords da un file di input e le incapsula in una lista
     * @param file istanza del File di stopwords da leggere.
     * @return lista di stopwords lette
     * @throws IOException
     */
    private static ArrayList<String> IOStopWords(File file) throws IOException {
        ArrayList<String> stopWords = new ArrayList<String>();
        try {
            BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String str = null;
            while ((str = bf.readLine()) != null) {
                if (!str.equals("")) {
                    stopWords.add(str);
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return stopWords;
    }

    /**
     * Implementazione dell'algoritmo di Lesk
     * @param syns Array di synset associato al concetto da disambiguare
     * @param sentence frase da disambiguare
     * @param stopWords elenco di stopwords a filtrare
     * @return il miglior synset associato al concetto
     */
    static Synset leskAlgorithm(Synset[] syns, String sentence, ArrayList<String> stopWords) {
        Synset bestSynset = syns[0];
        int maxOverlap = 0;
        int overlap;
        List<String> stemmedCtx = computeStemsWithStanford(filterStopWords(sentence, stopWords));
        for (Synset synset : syns) {
            overlap = 0;
            String bestSense = synset.getWordForms()[0];
            String gloss = synset.getDefinition().replaceAll(";", "");
            String[] examples = synset.getUsageExamples();
            List<String> stemmedGloss = computeStemsWithStanford(filterStopWords(gloss, stopWords));
            List<String> stemmedExamples = new ArrayList<String>();
            for (String example : examples) {
                example = example.replaceAll("\"", "").replaceAll(";", "").replaceAll(",", "").replaceAll("\\.", "");
                stemmedExamples.addAll(computeStemsWithStanford(filterStopWords(example, stopWords)));
            }
            if (stemmedExamples != null) {
                stemmedGloss.addAll(stemmedExamples);
            }
            overlap = computeOverlap(stemmedCtx, stemmedGloss);
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestSynset = synset;
            }
        }
        return bestSynset;
    }

    /**
     * Questo metodo calcola l'overlap
     * @param context contesto di disambiguazione
     * @param signature glossa più esempi associati al synset
     * @return valore di overlap
     */
    private static int computeOverlap(List<String> context, List<String> signature) {
        int overlap = 0;
        for (Iterator<String> it = context.iterator(); it.hasNext();) {
            String c = it.next();
            if (signature.contains(c)) {
                overlap++;
            }
        }
        return overlap;
    }

    /**
     * Inizializzazione dell'input incapsulando le frasi con la relativa parola da disambiguare all'interno di un dizionario.
     */
    private static void initInput() {
        inputSentencesMap.put("arms bend at the elbow", "arms");
        inputSentencesMap.put("germany sells arms to Saudi Arabia", "arms");
        inputSentencesMap.put("the key broke in the lock", "key");
        inputSentencesMap.put("the key problem was not one of quality but of quantity", "key");
        inputSentencesMap.put("work out the solution in your head", "solution");
        inputSentencesMap.put("heat the solution to 75° Celsius", "solution");
        inputSentencesMap.put("the house was burnt to ashes while the owner returned", "ashes");
        inputSentencesMap.put("this table is made of ash wood", "ash");
        inputSentencesMap.put("the lunch with her boss took longer than she expected", "lunch");
        inputSentencesMap.put("she packed her lunch in her purse", "lunch");
        inputSentencesMap.put("the classification of the genetic data took two years", "classification");
        inputSentencesMap.put("the journal Science published the classification this month", "classification");
        inputSentencesMap.put("his cottage is near a small wood", "wood");
        inputSentencesMap.put("the statue was made out of a block of wood", "wood");
    }

    /**
     * Questo metodo esegue il filtraggio delle stopWords dalla frase passata in input.
     * @param sentence la frase da filtrare
     * @param stopWords elenco di stopwords
     * @return l'elenco delle parole della frase filtrate.
     */
    private static ArrayList<String> filterStopWords(String sentence, ArrayList<String> stopWords) {
        ArrayList<String> filteredWords = new ArrayList<String>();
        String[] tokenizedWords = sentence.split(" ");
        for (String s : tokenizedWords) {
            if (!stopWords.contains(s)) {
                filteredWords.add(s);
            }
        }
        return filteredWords;
    }

    /**
     * Questo metodo si serve del parser di Stanford per effettuare la lemmatizzazione delle parole passate in input.
     * @param filteredWords elenco di parole da lemmatizzare
     * @return lista dei lemmi
     */
    private static List<String> computeStemsWithStanford(ArrayList<String> filteredWords){
        List<String> stemmedWords = new ArrayList<>();
        StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {
            {
                setProperty("annotators", "tokenize,ssplit,pos,lemma");
            }
        });

        for (String word : filteredWords) {
            Annotation tokenAnnotation = new Annotation(word);
            pipeline.annotate(tokenAnnotation);  // necessary for the LemmaAnnotation to be set.
            List<CoreMap> list = tokenAnnotation.get(SentencesAnnotation.class);
            String tokenLemma = list
                    .get(0).get(TokensAnnotation.class)
                    .get(0).get(LemmaAnnotation.class);
            stemmedWords.add(tokenLemma);
        }
        return stemmedWords;
    }

    /**
     * Questo metodo utilizza il programma Stemmer.java per effettuare la lemmatizzazione delle parole passate in input.
     * @param filteredWords elenco di parole da lemmatizzare
     * @return lista dei lemmi
     */
    private static List<String> computeStems(ArrayList<String> filteredWords) {
        List<String> stems = new ArrayList<>();
        try {
            File f = new File(pathToStemmerFile);
            String line = null;
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f)));
            for (String str : filteredWords) {
                out.write(str);
                out.write("\n");
            }
            out.close();
            String compileCmd = "javac  -classpath src/stemmer Stemmer.java";
            String executeCmd = "javac  -classpath src/stemmer Stemmer.java";
            stems = runStemmer("java Stemmer input.txt");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return stems;
    }

    /**
     *
     * @param command
     * @return
     * @throws Exception
     */
    private static ArrayList<String> runStemmer(String command) throws Exception {
        ArrayList<String> stems = new ArrayList<String>();
        Process pro = Runtime.getRuntime().exec(command);
        printLinesStemmer(command + " stdout:", pro.getInputStream(), stems);
        pro.waitFor();
        return stems;
    }

    private static void printLinesStemmer(String cmd, InputStream ins, ArrayList<String> stems) throws Exception {
        String line = null;
        BufferedReader in = new BufferedReader(
                new InputStreamReader(ins));
        while ((line = in.readLine()) != null) {
            stems.add(line);
        }
        in.close();
    }

    /**
     * Questo metodo lancia un processo specificato dal comando passato in input
     * @param command il comando che deve lanciare il processo
     * @throws Exception
     */
    private static void runProcess(String command) throws Exception {
        Process pro = Runtime.getRuntime().exec(command);
        pro.waitFor();
    }

    /**
     * Questo metodo viene usato per debugging. Stampa l'output di un comando eseguito mediante il metodo runProcess.
     * @param cmd comando da debuggare
     * @param ins input stream da cui leggere
     * @throws Exception
     */
    private static void printLines(String cmd, InputStream ins) throws Exception {
        String line = null;
        BufferedReader in = new BufferedReader(
                new InputStreamReader(ins));
        while ((line = in.readLine()) != null) {
            System.out.println(cmd + " " + line);
        }
        in.close();
    }

}