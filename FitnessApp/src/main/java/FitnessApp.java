import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;



/**
 * CustomGymRoutineApp class is a Swing-based GUI application to manage custom gym routines.
 * It allows users to generate, save, and log workouts using an external API.
 */
public class FitnessApp extends JFrame {

    private JTabbedPane tabbedPane;
    private JPanel workoutGeneratorPanel, savedWorkoutsPanel, loggedWorkoutsPanel;
    private JComboBox<String> muscleComboBox, difficultyComboBox, typeComboBox;
    private JTextField workoutNameField;
    private JPanel resultPanel, exerciseInputPanel;
    private JTextArea workoutDetailsArea;
    private java.util.List<JCheckBox> workoutCheckBoxes;
    private java.util.List<JCheckBox> selectedWorkoutCheckBoxes;
    private TreeMap<String, java.util.List<Exercise>> savedWorkoutGroups;
    private LinkedList<Map<String, java.util.List<String>>> loggedWorkoutsList;
    private HashSet<String> uniqueMuscleGroups;
    private JList<String> savedWorkoutList, loggedWorkoutList;
    private String selectedWorkoutName;
    private java.util.List<ExerciseInput> exerciseInputs;

    //Nutrition Side-------------------------------------------------------------------------------
    // API key and base URLs for the USDA API
    private static final String API_KEY = "d5G0uUGaqF6XcItKUd70Oc60uvl0AQh7luijlRU1";
    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1/foods/search";
    private static final String DETAILS_URL = "https://api.nal.usda.gov/fdc/v1/food/";

    // Swing GUI components
    private DefaultListModel<String> searchListModel;
    private JList<String> searchResultList;
    private JTextArea foodDetailsArea;
    private JTextField searchField;
    private JTextField portionSizeField;

    //Nutrition Panels
    private JPanel foodSearchPanel, mealLogPanel;


    // Data structures for managing food and meal logs
    // Map for food name to foodID
    private Map<String, String> foodIdMap = new HashMap<>();
    // Nested map for daily meal logs. It goes Date -> Meal Type -> List of Meals
    private Map<String, Map<String, DefaultListModel<String>>> dailyMealLogs;
    // List to display logged meals
    private JList<String> loggedMealsList;

    // Nutrient totals for the day
    private double totalCalories = 0;
    private double totalProtein = 0;
    private double totalCarbs = 0;
    private double totalFats = 0;

    // Label to display daily totals of nutrients
    private JLabel totalsLabel;

    public FitnessApp() {
        setTitle("Custom Gym Routine");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        workoutCheckBoxes = new ArrayList<>();
        selectedWorkoutCheckBoxes = new ArrayList<>();
        savedWorkoutGroups = new TreeMap<>();
        loggedWorkoutsList = new LinkedList<>();
        uniqueMuscleGroups = new HashSet<>();

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));

        workoutGeneratorPanel = createWorkoutGeneratorPanel();
        savedWorkoutsPanel = createSavedWorkoutsPanel();
        loggedWorkoutsPanel = createLoggedWorkoutsPanel();

        tabbedPane.addTab("Workout Generator", workoutGeneratorPanel);
        tabbedPane.addTab("Saved Workouts", savedWorkoutsPanel);
        tabbedPane.addTab("Logged Workouts", loggedWorkoutsPanel);

        //Nutrition Side---------------------------------------------------------------------------
        // Initialize the daily meal logs map
        dailyMealLogs = new HashMap<>();

        // Nutrition Panels
        foodSearchPanel = createFoodSearchPanel();
        mealLogPanel = createMealLogPanel();

        tabbedPane.addTab("Food Search", foodSearchPanel);
        tabbedPane.addTab("Meal Log", mealLogPanel);
        //------------------------------------------------------------------------------------------

        add(tabbedPane);
        setVisible(true);


    }
    /**
     * Panel to generate workouts by choosing options.
     * @return the workout generator panel
     */
    private JPanel createWorkoutGeneratorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel inputPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Enter Workout Details"));

        // "flexibility" removed from type options; "stretching" option included
        typeComboBox = new JComboBox<>(new String[]{"", "cardio", "strength", "stretching"});
        muscleComboBox = new JComboBox<>(new String[]{"", "abdominals", "biceps", "chest", "glutes", "hamstrings"});
        difficultyComboBox = new JComboBox<>(new String[]{"", "beginner", "intermediate", "expert"});
        workoutNameField = new JTextField();

        inputPanel.add(createLabel("Exercise Type:"));
        inputPanel.add(typeComboBox);
        inputPanel.add(createLabel("Muscle Group:"));
        inputPanel.add(muscleComboBox);
        inputPanel.add(createLabel("Difficulty:"));
        inputPanel.add(difficultyComboBox);
        inputPanel.add(createLabel("Workout Name:"));
        inputPanel.add(workoutNameField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton generateButton = createButton("Generate Workout");
        JButton saveButton = createButton("Save Selected as Custom Workout");

        generateButton.addActionListener(e -> fetchWorkoutData());
        saveButton.addActionListener(e -> saveCustomWorkout());

        buttonPanel.add(generateButton);
        buttonPanel.add(saveButton);

        resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
        JScrollPane resultScrollPane = new JScrollPane(resultPanel);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("Generated Workouts"));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(inputPanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(resultScrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Fetch workout data from API based on selected options.
     */
    private void fetchWorkoutData() {
        String type = (String) typeComboBox.getSelectedItem();
        String muscle = (String) muscleComboBox.getSelectedItem();
        String difficulty = (String) difficultyComboBox.getSelectedItem();

        StringBuilder apiUrl = new StringBuilder("https://api.api-ninjas.com/v1/exercises?");
        if (type != null && !type.isEmpty()) apiUrl.append("type=").append(type).append("&");
        if (muscle != null && !muscle.isEmpty()) apiUrl.append("muscle=").append(muscle).append("&");
        if (difficulty != null && !difficulty.isEmpty()) apiUrl.append("difficulty=").append(difficulty).append("&");
        if (apiUrl.charAt(apiUrl.length() - 1) == '&') apiUrl.setLength(apiUrl.length() - 1);

        try {
            URL url = new URL(apiUrl.toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Api-Key", "ViDZePNbkVmheKpy62fxYQ==xIjZAovy9dXD0DR5");
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();
            connection.disconnect();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(content.toString());

            resultPanel.removeAll();
            workoutCheckBoxes.clear();
            for (JsonNode node : root) {
                String name = node.get("name").asText();
                String exerciseType = node.has("type") ? node.get("type").asText() : "N/A";
                String muscleGroup = node.get("muscle").asText();
                String equipmentRequired = node.get("equipment").asText();

                String workout = String.format("Exercise: %s\nType: %s\nMuscle: %s\nEquipment: %s\n", name, exerciseType, muscleGroup, equipmentRequired);
                JCheckBox checkBox = new JCheckBox("<html>" + workout.replace("\n", "<br>") + "</html>");
                checkBox.addActionListener(e -> {
                    if (checkBox.isSelected() && !selectedWorkoutCheckBoxes.contains(checkBox)) {
                        selectedWorkoutCheckBoxes.add(checkBox);
                    } else if (!checkBox.isSelected()) {
                        selectedWorkoutCheckBoxes.remove(checkBox);
                    }
                });
                resultPanel.add(checkBox);
                workoutCheckBoxes.add(checkBox);
            }
            resultPanel.revalidate();
            resultPanel.repaint();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to fetch data from API.", "API Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    /**
     * Panel to manage saved workouts.
     * @return the saved workouts panel
     */
    private JPanel createSavedWorkoutsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        savedWorkoutList = new JList<>();
        savedWorkoutList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedWorkoutList.addListSelectionListener(e -> displayWorkoutExercises());

        JScrollPane listScrollPane = new JScrollPane(savedWorkoutList);
        listScrollPane.setBorder(BorderFactory.createTitledBorder("Saved Custom Workouts"));
        listScrollPane.setPreferredSize(new Dimension(250, 0));

        exerciseInputPanel = new JPanel();
        exerciseInputPanel.setLayout(new BoxLayout(exerciseInputPanel, BoxLayout.Y_AXIS));
        JScrollPane exerciseInputScrollPane = new JScrollPane(exerciseInputPanel);
        exerciseInputScrollPane.setBorder(BorderFactory.createTitledBorder("Exercise Details"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton logWorkoutButton = createButton("Log Workout");
        logWorkoutButton.addActionListener(e -> logWorkoutDetails());
        buttonPanel.add(logWorkoutButton);

        panel.add(listScrollPane, BorderLayout.WEST);
        panel.add(exerciseInputScrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Panel to view logged workouts.
     * @return the logged workouts panel
     */
    private JPanel createLoggedWorkoutsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        loggedWorkoutList = new JList<>();
        loggedWorkoutList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loggedWorkoutList.addListSelectionListener(e -> displayLoggedWorkoutDetails(loggedWorkoutList.getSelectedValue()));

        JScrollPane listScrollPane = new JScrollPane(loggedWorkoutList);
        listScrollPane.setBorder(BorderFactory.createTitledBorder("Logged Workouts"));
        listScrollPane.setPreferredSize(new Dimension(300, 0));

        workoutDetailsArea = new JTextArea(15, 30);
        workoutDetailsArea.setEditable(false);
        JScrollPane detailsScrollPane = new JScrollPane(workoutDetailsArea);
        detailsScrollPane.setBorder(BorderFactory.createTitledBorder("Workout Details"));

        updateLoggedWorkoutList();

        panel.add(listScrollPane, BorderLayout.WEST);
        panel.add(detailsScrollPane, BorderLayout.CENTER);

        return panel;
    }
    /**
     * Save a custom workout with selected exercises.
     * Adds selected exercises to a named workout group in savedWorkoutGroups.
     */
    private void saveCustomWorkout() {
        String workoutName = workoutNameField.getText().trim();

        if (workoutName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name for your workout.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<Exercise> selectedExercises = new ArrayList<>();
        for (JCheckBox checkBox : selectedWorkoutCheckBoxes) {
            String[] lines = checkBox.getText().replace("<html>", "").replace("</html>", "").split("<br>");
            String exerciseName = lines[0].replace("Exercise: ", "").trim();
            String exerciseType = lines[1].replace("Type: ", "").trim();

            if (exerciseType.equalsIgnoreCase("cardio")) {
                selectedExercises.add(new CardioExercise(exerciseName, exerciseType)); // No default duration
            } else if (exerciseType.equalsIgnoreCase("stretching")) {
                selectedExercises.add(new StretchingExercise(exerciseName, exerciseType)); // No default reps or duration
            } else {
                selectedExercises.add(new StrengthExercise(exerciseName, exerciseType)); // No default reps, sets, or weight
            }
        }

        savedWorkoutGroups.put(workoutName, selectedExercises);
        updateSavedWorkoutList();
        JOptionPane.showMessageDialog(this, "Workout saved successfully as " + workoutName + "!", "Save Confirmation", JOptionPane.INFORMATION_MESSAGE);

        workoutNameField.setText("");
        selectedWorkoutCheckBoxes.clear();
    }

    /**
     * Update the list of saved workouts in the GUI.
     */
    private void updateSavedWorkoutList() {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String workoutName : savedWorkoutGroups.keySet()) listModel.addElement(workoutName);
        savedWorkoutList.setModel(listModel);
    }

    /**
     * Display exercises for the selected workout in the saved workouts panel.
     */
    private void displayWorkoutExercises() {
        selectedWorkoutName = savedWorkoutList.getSelectedValue();
        exerciseInputPanel.removeAll();
        exerciseInputs = new ArrayList<>();
        if (selectedWorkoutName != null) {
            java.util.List<Exercise> exercises = savedWorkoutGroups.get(selectedWorkoutName);
            for (Exercise exercise : exercises) {
                ExerciseInput input = new ExerciseInput(exercise);
                exerciseInputs.add(input);
                exerciseInputPanel.add(input.getPanel());
            }
            exerciseInputPanel.revalidate();
            exerciseInputPanel.repaint();
        }
    }

    /**
     * Log workout details for the selected workout group.
     */
    private void logWorkoutDetails() {
        if (selectedWorkoutName == null) {
            JOptionPane.showMessageDialog(this, "Please select a workout group to log details.", "Selection Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<String> workoutDetails = new ArrayList<>();
        for (ExerciseInput input : exerciseInputs) {
            workoutDetails.add(input.getExerciseDetails()); // Now includes user-entered details
        }
        String date = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
        String logEntryName = selectedWorkoutName + " (" + date + ")";
        Map<String, java.util.List<String>> logEntry = new HashMap<>();
        logEntry.put(logEntryName, workoutDetails);
        loggedWorkoutsList.add(logEntry);
        updateLoggedWorkoutList();
        JOptionPane.showMessageDialog(this, "Workout logged successfully!", "Log Confirmation", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Update the list of logged workouts in the GUI.
     */
    private void updateLoggedWorkoutList() {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (Map<String, java.util.List<String>> entry : loggedWorkoutsList) {
            for (String workoutName : entry.keySet()) listModel.addElement(workoutName);
        }
        loggedWorkoutList.setModel(listModel);
    }

    /**
     * Display detailed view of a logged workout.
     * @param workoutName the name of the logged workout to display
     */
    private void displayLoggedWorkoutDetails(String workoutName) {
        for (Map<String, java.util.List<String>> entry : loggedWorkoutsList) {
            if (entry.containsKey(workoutName)) {
                StringBuilder details = new StringBuilder("Workout: " + workoutName).append("\n------------------------------------\n");
                for (String exerciseDetail : entry.get(workoutName)) {
                    details.append(exerciseDetail.replace("Type:", "").replace("Difficulty:", ""))
                            .append("\n------------------------------------\n");
                }
                workoutDetailsArea.setText(details.toString());
                return;
            }
        }
        workoutDetailsArea.setText("");
    }
    /**
     * Abstract class representing a generic exercise.
     */
    abstract class Exercise {
        protected String name;
        protected String muscleGroup;

        public Exercise(String name, String muscleGroup) {
            this.name = name;
            this.muscleGroup = muscleGroup;
        }

        /**
         * Get detailed information about the exercise.
         * @return a string containing exercise details
         */
        public abstract String getDetails();
    }

    /**
     * StrengthExercise class representing a strength-based exercise.
     */
    class StrengthExercise extends Exercise {
        public StrengthExercise(String name, String muscleGroup) {
            super(name, muscleGroup);
        }

        @Override
        public String getDetails() {
            return "Strength Exercise: " + name + ", Muscle Group: " + muscleGroup;
        }
    }

    /**
     * CardioExercise class representing a cardio-based exercise.
     */
    class CardioExercise extends Exercise {
        public CardioExercise(String name, String muscleGroup) {
            super(name, muscleGroup);
        }

        @Override
        public String getDetails() {
            return "Cardio Exercise: " + name + ", Muscle Group: " + muscleGroup;
        }
    }

    /**
     * StretchingExercise class representing a stretching exercise.
     */
    class StretchingExercise extends Exercise {
        public StretchingExercise(String name, String muscleGroup) {
            super(name, muscleGroup);
        }

        @Override
        public String getDetails() {
            return "Stretching Exercise: " + name + ", Muscle Group: " + muscleGroup;
        }
    }

    /**
     * ExerciseInput helper class for managing individual exercise inputs.
     * Allows users to enter specific values (reps, sets, weight, or duration).
     */
    class ExerciseInput {
        private Exercise exercise;
        private JTextField repsField, weightField, durationField, setsField;

        public ExerciseInput(Exercise exercise) {
            this.exercise = exercise;
        }

        /**
         * Get the panel displaying exercise details with relevant input fields.
         * @return a JPanel with exercise information and input fields for reps, weight, sets, or duration.
         */
        public JPanel getPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(0, 1)); // Two columns for label and input

            JLabel nameLabel = new JLabel(exercise.getDetails());
            panel.add(nameLabel);
            panel.add(new JLabel()); // Empty label for spacing

            // Display relevant fields based on the type of exercise
            if (exercise instanceof StrengthExercise) {
                repsField = new JTextField(2);
                weightField = new JTextField(2);
                setsField = new JTextField(2);

                panel.add(new JLabel("Sets:"));
                panel.add(setsField);
                panel.add(new JLabel("Reps:"));
                panel.add(repsField);
                panel.add(new JLabel("Weight (lbs):"));
                panel.add(weightField);
            } else if (exercise instanceof CardioExercise) {
                durationField = new JTextField(2);

                panel.add(new JLabel("Duration (mins):"));
                panel.add(durationField);
            } else if (exercise instanceof StretchingExercise) {
                durationField = new JTextField(2);
                repsField = new JTextField(2);

                panel.add(new JLabel("Duration (mins):"));
                panel.add(durationField);
                panel.add(new JLabel("Reps:"));
                panel.add(repsField);
            }

            return panel;
        }

        /**
         * Get a string with exercise details, including user inputs for logging.
         * @return a string with all exercise details and user inputs.
         */
        public String getExerciseDetails() {
            StringBuilder details = new StringBuilder(exercise.getDetails());

            // Append user inputs to the details if available
            if (exercise instanceof StrengthExercise) {
                String sets = setsField.getText().trim();
                String reps = repsField.getText().trim();
                String weight = weightField.getText().trim();
                details.append(", Sets: ").append(sets.isEmpty() ? "N/A" : sets)
                        .append(", Reps: ").append(reps.isEmpty() ? "N/A" : reps)
                        .append(", Weight: ").append(weight.isEmpty() ? "N/A" : weight);
            } else if (exercise instanceof CardioExercise) {
                String duration = durationField.getText().trim();
                details.append(", Duration: ").append(duration.isEmpty() ? "N/A" : duration).append(" mins");
            } else if (exercise instanceof StretchingExercise) {
                String duration = durationField.getText().trim();
                String reps = repsField.getText().trim();
                details.append(", Duration: ").append(duration.isEmpty() ? "N/A" : duration).append(" mins")
                        .append(", Reps: ").append(reps.isEmpty() ? "N/A" : reps);
            }

            return details.toString();
        }
    }
    /**
     * Main method to start the application.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FitnessApp());
    }

    /**
     * Utility method to create a label with right alignment.
     * @param text the text for the label
     * @return a JLabel with the specified text and right alignment
     */
    private JLabel createLabel(String text) {
        return new JLabel(text, JLabel.RIGHT);
    }

    /**
     * Utility method to create a button.
     * @param text the text for the button
     * @return a JButton with the specified text
     */
    private JButton createButton(String text) {
        return new JButton(text);
    }

    //Nutrition side----------------------------------------------------------------------------------------------

    /**
     * Creates the food search panel with GUI components and search functionality
     * @return the food search panel
     */
    private JPanel createFoodSearchPanel() {
        // Create the main search panel
        JPanel searchPanel = new JPanel(new BorderLayout());

        // Panel for search input
        JPanel inputPanel = new JPanel();
        // TextField and Button for food search
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");

        // Adding a label, text field, and search button to panel
        inputPanel.add(new JLabel("Enter food:"));
        inputPanel.add(searchField);
        inputPanel.add(searchButton);

        // List for displaying search results
        searchListModel = new DefaultListModel<>();
        searchResultList = new JList<>(searchListModel);
        // Scroll pane for the list
        JScrollPane searchScrollPane = new JScrollPane(searchResultList);

        // Text area to display food details
        foodDetailsArea = new JTextArea();
        foodDetailsArea.setEditable(false);
        // Scroll pane for the text area
        JScrollPane detailsScrollPane = new JScrollPane(foodDetailsArea);

        // Panel for portion size input and log button
        JPanel logPanel = new JPanel();
        portionSizeField = new JTextField(10);
        JButton logButton = new JButton("Log Food");
        logPanel.add(new JLabel("Portion Size(g):"));
        logPanel.add(portionSizeField);
        logPanel.add(logButton);

        // Add components to the search panel
        searchPanel.add(inputPanel, BorderLayout.NORTH);
        searchPanel.add(searchScrollPane, BorderLayout.CENTER);
        searchPanel.add(detailsScrollPane, BorderLayout.SOUTH);
        searchPanel.add(logPanel, BorderLayout.SOUTH);

        // Define action listener for the search button
        searchButton.addActionListener(e -> searchFood(searchField.getText()));

        // Define action listener for the log meal button
        logButton.addActionListener(e -> logMeal());

        return searchPanel;
    }

    /**
     * Creates the meal log panel with logged meals and daily totals
     * @return the meal log panel
     */
    private JPanel createMealLogPanel() {
        // Create the main meal log panel
        JPanel mealLogPanel = new JPanel(new BorderLayout());

        // List to display logged meals
        loggedMealsList = new JList<>();
        JScrollPane loggedMealsScrollPane = new JScrollPane(loggedMealsList);

        // Label to display daily nutrient totals
        totalsLabel = new JLabel("Daily Totals - Calories: 0, Protein: 0g, Carbs: 0g, Fats: 0g");
        // Add components to the meal log panel
        mealLogPanel.add(totalsLabel, BorderLayout.NORTH);
        mealLogPanel.add(loggedMealsScrollPane, BorderLayout.CENTER);

        return mealLogPanel;
    }

    /**
     * Searches for food based on the query entered by the user
     * @param query the search query
     * @return the search results
     */
    private void searchFood(String query) {
        try {

            // Encoding the search query to handle special characters
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlStr = BASE_URL + "?query=" + encodedQuery + "&api_key=" + API_KEY;

            // Create URL and connection
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Get the response from the connection
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            // Append each line of the response to the response StringBuilder
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse and update the GUI with the search results
            parseSearchResults(response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the search results JSON response and updates the list
     * @param jsonResponse the JSON response
     * @return the search results
     */
    private void parseSearchResults(String jsonResponse) {
        // Parse the JSON response
        JSONObject obj = new JSONObject(jsonResponse);

        // Check if the JSON object contains a key named "foods"
        if (obj.has("foods")) {
            JSONArray foods = obj.getJSONArray("foods");
            // Clear previous search results
            searchListModel.clear();
            foodIdMap.clear();

            // Iterate over the foods and extract relevant information
            for (int i = 0; i < foods.length(); i++) {
                JSONObject food = foods.getJSONObject(i);
                String foodName = food.getString("description");

                // Extract foodId and handle different types of values
                String foodId = "";
                // Check if the food has a "fdcId" key
                if (food.has("fdcId")) {
                    Object fdcIdObj = food.get("fdcId");
                    if (fdcIdObj instanceof Integer) {
                        // If "fdcId" is an integer, convert it to a string
                        foodId = String.valueOf(fdcIdObj);
                    } else {
                        foodId = (String) fdcIdObj;
                    }
                }

                // Add food name to the list and map it to the foodId
                searchListModel.addElement(foodName);
                foodIdMap.put(foodName, foodId);
            }
        }
    }

    /**
     * Logs a meal by retrieving detailed information and updating the daily log
     * @return the logged meal
     */
    private void logMeal() {
        String selectedFood = searchResultList.getSelectedValue();
        String portionSizeStr = portionSizeField.getText();

        if (selectedFood != null && !portionSizeStr.isEmpty()) {
            try {
                // Try parsing the portion size input
                double userPortionSize = Double.parseDouble(portionSizeStr);

                // Check if the portion size is a valid positive number
                if (userPortionSize <= 0) {
                    JOptionPane.showMessageDialog(this, "Please enter a positive portion size.");
                    return;
                }

                // Retrieve the food ID and make the API call to get food details
                String foodId = foodIdMap.get(selectedFood);
                String urlStr = DETAILS_URL + foodId + "?api_key=" + API_KEY;
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Get the response from the connection
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                // Append each line of the response to the response StringBuilder
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject foodDetails = new JSONObject(response.toString());

                // Extract the default portion size from the API response
                double apiPortionSize = foodDetails.optDouble("servingSize", 100);
                String apiUnit = foodDetails.optString("servingSizeUnit", "g");

                // Scale nutrients based on portion size ratio
                double portionRatio = userPortionSize / apiPortionSize;

                updateNutrientTotals(foodDetails, portionRatio);

                // Prompt for meal type and log the meal
                String[] mealTypes = {"Breakfast", "Lunch", "Dinner", "Snack"};
                String mealType = (String) JOptionPane.showInputDialog(this,
                        "Select meal type:", "Meal Type", JOptionPane.PLAIN_MESSAGE,
                        null, mealTypes, mealTypes[0]);

                // Add the logged meal to the daily log
                if (mealType != null) {
                    // Get the current date
                    LocalDate currentDate = LocalDate.now();
                    // Convert the current date to a key for the daily meal logs
                    String dateKey = currentDate.toString();

                    // Initialize the daily meal logs map if it doesn't exist
                    dailyMealLogs.putIfAbsent(dateKey, new HashMap<>());
                    // Get the meals for the current day
                    Map<String, DefaultListModel<String>> mealsForDay = dailyMealLogs.get(dateKey);

                    // Initialize the meal type list if it doesn't exist
                    mealsForDay.putIfAbsent(mealType, new DefaultListModel<>());
                    // Add the logged meal to the meal type list
                    mealsForDay.get(mealType).addElement(selectedFood + " (" + userPortionSize + apiUnit + ")");

                    // Update the UI to display the newly logged meals
                    updateLoggedMealsList();
                }

            } catch (NumberFormatException e) {
                // Handle invalid portion size input (not a number)
                JOptionPane.showMessageDialog(this, "Invalid portion size. Please enter a numeric value.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please select a food and enter portion size.");
        }
    }

    /**
     * Updates the nutrient totals based on the food details and portion ratio
     * @param foodDetails the food details JSON object
     * @param portionRatio the portion ratio
     */
    private void updateNutrientTotals(JSONObject foodDetails, double portionRatio) {

        // Get the foodNutrients array from the foodDetails response
        JSONArray foodNutrients = foodDetails.optJSONArray("foodNutrients");

        // Check if foodNutrients is null or empty
        if (foodNutrients == null || foodNutrients.length() == 0) {
            System.out.println("No food nutrients found in the API response.");
            return;
        }

        // Iterate over the array to find the nutrients and update the totals
        for (int i = 0; i < foodNutrients.length(); i++) {
            JSONObject nutrient = foodNutrients.getJSONObject(i);
            JSONObject nutrientDetails = nutrient.optJSONObject("nutrient");

            // Ensure that nutrientDetails is not null
            if (nutrientDetails == null) {
                System.out.println("Missing nutrient details for index " + i);
                continue;
            }

            // Extract the nutrient name and value
            String nutrientName = nutrientDetails.optString("name");
            double nutrientValue = nutrient.optDouble("amount", 0);

            // Update the totals based on the nutrient name
            if (nutrientName.equalsIgnoreCase("Protein")) {
                totalProtein += nutrientValue * portionRatio;
            } else if (nutrientName.equalsIgnoreCase("Total lipid (fat)")) {
                totalFats += nutrientValue * portionRatio;
            } else if (nutrientName.equalsIgnoreCase("Carbohydrate, by difference")) {
                totalCarbs += nutrientValue * portionRatio;
            } else if (nutrientName.equalsIgnoreCase("Energy")) {
                totalCalories += nutrientValue * portionRatio;
            }
        }

        // Update the totals label to reflect the changes
        totalsLabel.setText(String.format(
                "Daily Totals - Calories: %.2f, Protein: %.2f g, Carbs: %.2f g, Fats: %.2f g",
                totalCalories, totalProtein, totalCarbs, totalFats
        ));
    }

    /**
     * Updates the logged meals list based on daily logs
     *
     */
    private void updateLoggedMealsList() {
        // Initialize the loggedMealsText StringBuilder
        StringBuilder loggedMealsText = new StringBuilder();

        // Get the current date to use as a key for the daily meal logs
        LocalDate currentDate = LocalDate.now();
        String dateKey = currentDate.toString();

        // Check if the daily meal logs contain the current date
        if (dailyMealLogs.containsKey(dateKey)) {
            // Get the meals for the current day
            Map<String, DefaultListModel<String>> mealsForDay = dailyMealLogs.get(dateKey);
            // Iterate over the meals for the current day
            for (Map.Entry<String, DefaultListModel<String>> entry : mealsForDay.entrySet()) {
                // Append the meal type to the loggedMealsText StringBuilder
                loggedMealsText.append(entry.getKey()).append(":\n");
                // Iterate over the food and portion sizes in the meal type list
                for (int i = 0; i < entry.getValue().getSize(); i++) {
                    // Add each meal, prefixed with a dash ("- ") to the text
                    loggedMealsText.append("- ").append(entry.getValue().get(i)).append("\n");
                }
                // Add a new line after each meal type for visual separation
                loggedMealsText.append("\n");
            }
        }

        // Split the logged meals text into lines and update the logged meals list with the data
        loggedMealsList.setListData(loggedMealsText.toString().split("\n"));
    }
}
