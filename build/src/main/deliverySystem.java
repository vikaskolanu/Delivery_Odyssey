import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;


// GraphPanel class to handle graph drawing
class GraphPanel extends JPanel {
    private Map<String, Map<String, Integer>> graph;
    private HashMap<String, Point> nodePositions;
    private List<Warehouse> warehouses;
    private String deliveryBoyLocation;
    private List<String> currentPath;
    private int currentPathIndex;
    private Map<String, Integer> customerOrders;
    private Map<String, String> customerPlatforms;

    public GraphPanel(Map<String, Map<String, Integer>> graph, HashMap<String, Point> nodePositions, List<Warehouse> warehouses) {
        this.graph = graph;
        this.nodePositions = nodePositions;
        this.warehouses = warehouses;
        this.customerOrders = new HashMap<>();
        this.customerPlatforms = new HashMap<>();
        this.deliveryBoyLocation = "A1"; // Default starting location
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawGraph(g);
    }

    private void drawGraph(Graphics g) {
        // Draw edges
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(3)); // Increase edge thickness
        g2d.setColor(Color.BLACK);
        for (String source : graph.keySet()) {
            for (Map.Entry<String, Integer> edge : graph.get(source).entrySet()) {
                String target = edge.getKey();
                Point p1 = nodePositions.get(source);
                Point p2 = nodePositions.get(target);
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);

                // Draw edge weights
                int weight = edge.getValue();
                g2d.setColor(Color.RED);
                g2d.setFont(new Font("Arial", Font.BOLD, 12)); // Increase font size for edge weights
                g2d.drawString(String.valueOf(weight), (p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
            }
        }

        // Draw nodes
        g2d.setStroke(new BasicStroke(1)); // Reset stroke for nodes
        for (Map.Entry<String, Point> entry : nodePositions.entrySet()) {
            String node = entry.getKey();
            Point p = entry.getValue();

            // Highlight warehouses
            for (Warehouse warehouse : warehouses) {
                if (warehouse.location.equals(node)) {
                    g2d.setColor(getPlatformColor(warehouse.platform));
                    g2d.fillRect(p.x - 15, p.y - 15, 30, 30); // Increase node size
                    break;
                }
            }

            // Highlight the delivery boy's location
            if (node.equals(deliveryBoyLocation)) {
                g2d.setColor(Color.GREEN);
                g2d.fillOval(p.x - 12, p.y - 12, 24, 24); // Increase node size
            }

            // Highlight customer locations
            if (customerOrders.containsKey(node)) {
                g2d.setColor(getPlatformColor(getPlatformForCustomer(node)));
                g2d.fillOval(p.x - 15, p.y - 15, 30, 30); // Increase node size
                g2d.setColor(Color.BLACK);
                g2d.drawOval(p.x - 15, p.y - 15, 30, 30); // Dark outline
                g2d.setFont(new Font("Arial", Font.BOLD, 14)); // Increase font size for order numbers
                g2d.drawString(String.valueOf(customerOrders.get(node)), p.x - 5, p.y + 5); // Order number
            }

            // Draw node labels
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12)); // Increase font size for node labels
            g2d.drawString(node, p.x - 10, p.y - 20); // Adjust label position
        }

        // Highlight the current path (only the remaining path)
        if (currentPath != null && currentPathIndex < currentPath.size()) {
            g2d.setColor(Color.GREEN);
            g2d.setStroke(new BasicStroke(3)); // Increase path thickness
            for (int i = currentPathIndex; i < currentPath.size() - 1; i++) {
                String source = currentPath.get(i);
                String target = currentPath.get(i + 1);
                Point p1 = nodePositions.get(source);
                Point p2 = nodePositions.get(target);
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

    private Color getPlatformColor(String platform) {
        if (platform == null) {
            return Color.BLUE; // Default color if platform is null
        }
        switch (platform.toLowerCase()) {
            case "swiggy":
                return Color.ORANGE;
            case "zepto":
                return Color.MAGENTA;
            case "blinkit":
                return Color.YELLOW;
            default:
                return Color.BLUE;
        }
    }

    private String getPlatformForCustomer(String customerLocation) {
        return customerPlatforms.get(customerLocation); // Return the platform for the customer location
    }

    public void setDeliveryBoyLocation(String location) {
        this.deliveryBoyLocation = location;
        repaint();
    }

    public void setCurrentPath(List<String> path) {
        this.currentPath = path;
        this.currentPathIndex = 0;
        repaint();
    }

    public void addCustomerOrder(String location, int orderNumber, String platform) {
        customerOrders.put(location, orderNumber);
        customerPlatforms.put(location, platform);
        repaint();
    }

    public Map<String, Map<String, Integer>> getGraph() {
        return graph;
    }

    public String getDeliveryBoyLocation() {
        return deliveryBoyLocation;
    }

    public List<String> getCurrentPath() {
        return currentPath;
    }

    public int getCurrentPathIndex() {
        return currentPathIndex;
    }

    public void setCurrentPathIndex(int index) {
        this.currentPathIndex = index;
        repaint();
    }

    public Map<String, Integer> getCustomerOrders() {
        return customerOrders;
    }

    public Map<String, String> getCustomerPlatforms() {
        return customerPlatforms;
    }
}

// Main deliverySystem class
public class deliverySystem extends JPanel {
    private GraphPanel graphPanel;
    private Timer deliveryTimer;
    private JButton generateOrderButton;
    private JButton customOrderButton;
    private JTextField customerLocationField;
    private JComboBox<String> platformComboBox;
    private List<Order> activeOrders;
    private int orderCounter;
    private JTextArea outputArea;
    private JTextArea guidelinesArea;

    public deliverySystem() {
        // Load graph data
        Map<String, Map<String, Integer>> graph = loadGraphFromJson("config.json");
        HashMap<String, Point> nodePositions = generateNodePositions(graph);
        List<Warehouse> warehouses = loadWarehousesFromJson("config.json");

        // Initialize components
        graphPanel = new GraphPanel(graph, nodePositions, warehouses);
        activeOrders = new ArrayList<>();
        orderCounter = 1;

        // Timer for automated movement
        deliveryTimer = new Timer(750, e -> moveDeliveryBoy());

        // Button to generate random orders
        generateOrderButton = new JButton("Generate Random Order");
        generateOrderButton.addActionListener(e -> generateRandomOrder());

        // Custom order input fields
        JPanel customOrderPanel = new JPanel();
        customOrderPanel.setLayout(new FlowLayout());
        customOrderPanel.add(new JLabel("Customer Location:"));
        customerLocationField = new JTextField(5);
        customOrderPanel.add(customerLocationField);
        customOrderPanel.add(new JLabel("Platform:"));
        platformComboBox = new JComboBox<>(new String[]{"Swiggy", "Zepto", "Blinkit"});
        customOrderPanel.add(platformComboBox);
        customOrderButton = new JButton("Add Custom Order");
        customOrderButton.addActionListener(e -> addCustomOrder());
        customOrderPanel.add(customOrderButton);

        // Text area to display delivery messages
        outputArea = new JTextArea(5, 40);
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        // Text area to display user guidelines
        guidelinesArea = new JTextArea(10, 40);
        guidelinesArea.setEditable(false);
        guidelinesArea.setText(
                "User Guidelines:\n" +
                        "1. Rectangular boxes represent warehouses. Their colors indicate the platform:\n" +
                        "   - Orange: Swiggy\n" +
                        "   - Magenta: Zepto\n" +
                        "   - Yellow: Blinkit\n" +
                        "2. Circular nodes represent locations. Green circle indicates the delivery boy's current location.\n" +
                        "3. Colored circles with numbers represent customer locations. The number is the order ID.\n" +
                        "4. The highlighted green path shows the remaining path to be traveled.\n" +
                        "5. Use the 'Generate Random Order' button to create random orders.\n" +
                        "6. Use the custom order form to add orders manually by specifying the customer location and platform."
        );
        JScrollPane guidelinesScrollPane = new JScrollPane(guidelinesArea);

        // Layout
        setLayout(new BorderLayout());

        // Create a top panel for buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(generateOrderButton);
        topPanel.add(customOrderPanel);

        // Create a split pane to divide the graph and guidelines
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(graphPanel); // Graph panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(guidelinesScrollPane, BorderLayout.CENTER);
        rightPanel.add(scrollPane, BorderLayout.SOUTH);
        splitPane.setRightComponent(rightPanel); // Guidelines and output panel
        splitPane.setDividerLocation(600); // Set initial divider position

        // Add the top panel and split pane to the main panel
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    private Map<String, Map<String, Integer>> loadGraphFromJson(String filePath) {
        Map<String, Map<String, Integer>> graph = new HashMap<>();
        try (FileReader reader = new FileReader(filePath, StandardCharsets.UTF_8)) {
            StringBuilder jsonData = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                jsonData.append(buffer, 0, read);
            }

            JSONObject json = new JSONObject(jsonData.toString());
            JSONArray nodes = json.getJSONArray("nodes");
            JSONArray edges = json.getJSONArray("edges");

            // Initialize nodes in the graph
            for (int i = 0; i < nodes.length(); i++) {
                String node = nodes.getString(i);
                graph.put(node, new HashMap<>());
            }

            // Add edges to the graph
            for (int i = 0; i < edges.length(); i++) {
                JSONArray edge = edges.getJSONArray(i);
                String source = edge.getString(0);
                String target = edge.getString(1);
                int weight = edge.getInt(2);
                graph.get(source).put(target, weight);
                graph.get(target).put(source, weight); // Assuming undirected graph
            }
        } catch (IOException e) {
            System.err.println("Error reading config.json: " + e.getMessage());
        }
        return graph;
    }

    private HashMap<String, Point> generateNodePositions(Map<String, Map<String, Integer>> graph) {
        HashMap<String, Point> positions = new HashMap<>();
        for (String node : graph.keySet()) {
            // Extract row and column from node ID (e.g., "A1" -> row 0, column 0)
            char rowChar = node.charAt(0);
            int row = rowChar - 'A'; // Convert 'A' to 0, 'B' to 1, etc.
            int col = Integer.parseInt(node.substring(1)) - 1; // Convert "1" to 0, "2" to 1, etc.
            // Assign positions with scaling (e.g., 50 pixels per unit)
            positions.put(node, new Point(col * 50 + 50, row * 50 + 50));
        }
        return positions;
    }

    private List<Warehouse> loadWarehousesFromJson(String filePath) {
        List<Warehouse> warehouses = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath, StandardCharsets.UTF_8)) {
            StringBuilder jsonData = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                jsonData.append(buffer, 0, read);
            }

            JSONObject json = new JSONObject(jsonData.toString());
            JSONArray warehouseArray = json.getJSONArray("warehouses");
            for (int i = 0; i < warehouseArray.length(); i++) {
                JSONObject warehouse = warehouseArray.getJSONObject(i);
                String platform = warehouse.getString("platform");
                String location = warehouse.getString("location");
                warehouses.add(new Warehouse(platform, location));
            }
        } catch (IOException e) {
            System.err.println("Error reading config.json: " + e.getMessage());
        }
        return warehouses;
    }

    private void generateRandomOrder() {
        Random rand = new Random();
        List<Warehouse> warehouses = loadWarehousesFromJson("config.json");
        Warehouse warehouse = warehouses.get(rand.nextInt(warehouses.size())); // Randomly select a warehouse
        String platform = warehouse.platform;
        List<String> nodes = new ArrayList<>(graphPanel.getGraph().keySet());
        String customerLocation = nodes.get(rand.nextInt(nodes.size()));

        // Assign order number to customer location and associate it with the platform
        graphPanel.addCustomerOrder(customerLocation, orderCounter++, platform);

        // Add the order to the active orders list
        activeOrders.add(new Order(warehouse.location, customerLocation));

        // Recalculate the optimal path for all active orders
        recalculateOptimalPath();
    }

    private void addCustomOrder() {
        String customerLocation = customerLocationField.getText().trim();
        String platform = (String) platformComboBox.getSelectedItem();

        if (!graphPanel.getGraph().containsKey(customerLocation)) {
            JOptionPane.showMessageDialog(this, "Invalid customer location!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Assign order number to customer location and associate it with the platform
        graphPanel.addCustomerOrder(customerLocation, orderCounter++, platform);

        // Find the nearest warehouse for the platform
        List<Warehouse> warehouses = loadWarehousesFromJson("config.json");
        Warehouse warehouse = findWarehouseForPlatform(platform, warehouses);
        if (warehouse == null) {
            JOptionPane.showMessageDialog(this, "No warehouse found for the platform!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Add the order to the active orders list
        activeOrders.add(new Order(warehouse.location, customerLocation));

        // Recalculate the optimal path for all active orders
        recalculateOptimalPath();
    }

    private Warehouse findWarehouseForPlatform(String platform, List<Warehouse> warehouses) {
        for (Warehouse warehouse : warehouses) {
            if (warehouse.platform.equalsIgnoreCase(platform)) {
                return warehouse;
            }
        }
        return null;
    }

    private void recalculateOptimalPath() {
        if (activeOrders.isEmpty()) {
            graphPanel.setCurrentPath(null);
            return;
        }

        // Start from the delivery boy's current location
        List<String> path = new ArrayList<>();
        String currentLocation = graphPanel.getDeliveryBoyLocation();

        // Create a copy of active orders to avoid modifying the original list
        List<Order> remainingOrders = new ArrayList<>(activeOrders);

        // Visit all warehouses and customer locations in the optimal order
        while (!remainingOrders.isEmpty()) {
            Order nextOrder = findNearestOrder(currentLocation, remainingOrders);
            if (nextOrder == null) {
                break; // No more orders to deliver
            }

            // Add the path to the warehouse and then to the customer
            List<String> pathToWarehouse = findShortestPath(currentLocation, nextOrder.warehouseLocation);
            List<String> pathToCustomer = findShortestPath(nextOrder.warehouseLocation, nextOrder.customerLocation);

            path.addAll(pathToWarehouse);
            path.addAll(pathToCustomer);

            // Update the current location to the customer's location
            currentLocation = nextOrder.customerLocation;

            // Remove the delivered order from the remaining orders list
            remainingOrders.remove(nextOrder);
        }

        graphPanel.setCurrentPath(path);
        deliveryTimer.start();
    }

    private Order findNearestOrder(String currentLocation, List<Order> orders) {
        Order nearestOrder = null;
        int minDistance = Integer.MAX_VALUE;

        for (Order order : orders) {
            // Calculate the total distance: currentLocation -> warehouse -> customer
            List<String> pathToWarehouse = findShortestPath(currentLocation, order.warehouseLocation);
            List<String> pathToCustomer = findShortestPath(order.warehouseLocation, order.customerLocation);
            int totalDistance = pathToWarehouse.size() + pathToCustomer.size();

            if (totalDistance < minDistance) {
                minDistance = totalDistance;
                nearestOrder = order;
            }
        }

        return nearestOrder;
    }

    private List<String> findShortestPath(String start, String end) {
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<String> queue = new PriorityQueue<>(Comparator.comparingInt(node -> distances.get(node)));

        // Initialize distances
        for (String node : graphPanel.getGraph().keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }
        distances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(end)) {
                break; // Found the shortest path to the destination
            }

            for (Map.Entry<String, Integer> neighbor : graphPanel.getGraph().get(current).entrySet()) {
                String next = neighbor.getKey();
                int newDistance = distances.get(current) + neighbor.getValue();
                if (newDistance < distances.get(next)) {
                    distances.put(next, newDistance);
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }

        // Reconstruct the path
        List<String> path = new ArrayList<>();
        for (String node = end; node != null; node = previous.get(node)) {
            path.add(node);
        }
        Collections.reverse(path);
        return path;
    }

    private void moveDeliveryBoy() {
        List<String> currentPath = graphPanel.getCurrentPath();
        int currentPathIndex = graphPanel.getCurrentPathIndex();

        if (currentPath != null && currentPathIndex < currentPath.size()) {
            // Update the delivery boy's location
            String nextLocation = currentPath.get(currentPathIndex);
            graphPanel.setDeliveryBoyLocation(nextLocation);

            // Update the path index AFTER updating the delivery boy's position
            graphPanel.setCurrentPathIndex(currentPathIndex + 1);

            // Check if the delivery boy has reached a customer location
            if (graphPanel.getCustomerOrders().containsKey(nextLocation)) {
                // Mark the order as delivered
                int orderNumber = graphPanel.getCustomerOrders().get(nextLocation);
                String platform = graphPanel.getCustomerPlatforms().get(nextLocation);
                outputArea.append("Order #" + orderNumber + " (" + platform + ") delivered to " + nextLocation + ".\n");

                // Remove the delivered order
                activeOrders.removeIf(order -> order.customerLocation.equals(nextLocation));
                graphPanel.getCustomerOrders().remove(nextLocation);
                graphPanel.getCustomerPlatforms().remove(nextLocation);
            }

            // Repaint the graph immediately after updating the delivery boy's position and path
            graphPanel.repaint();
        } else {
            // Reset the path after all orders are delivered
            graphPanel.setCurrentPath(null);
            deliveryTimer.stop(); // Stop the timer when all orders are delivered
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Delivery Graph Visualization");
            deliverySystem panel = new deliverySystem();
            frame.add(panel);
            frame.setSize(1200, 800); // Increased width to accommodate the split pane
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    // Inner class to represent an order
    private class Order {
        String warehouseLocation;
        String customerLocation;

        Order(String warehouseLocation, String customerLocation) {
            this.warehouseLocation = warehouseLocation;
            this.customerLocation = customerLocation;
        }
    }
}