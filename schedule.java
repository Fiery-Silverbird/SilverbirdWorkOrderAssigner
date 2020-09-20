import com.mongodb.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Scanner;

public class schedule {

    public static final String auth_id = "AC598615db2678ed3520e90447ad04d65d";
    public static final String auth_tkn = "575198bbc796e99a2ae125518713e478";

    public static void convertXLSXtoCSVAndAddToDatabase(Sheet sheet, String fileName, DB database) {
        //reads the provided xlsx file and converts it to a .csv file by using the apache poi library
        //finally this adds the data to a specifically created mongoDB database
        String rowData = "";
        Row row = null;
        int checkFirstPass = 0;
        LinkedList<String> attributes = new LinkedList<>();
        for (int i = 0; i < sheet.getLastRowNum()+1; i++) {
            row = sheet.getRow(i);
            if(row != null && !row.equals("")) {
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    if(row.getCell(j) != null &&  row.getCell(j).getCellType() != Cell.CELL_TYPE_BLANK) {
                        if(checkFirstPass == 0) {
                            attributes.add(row.getCell(j).toString());
                        }
                        rowData += row.getCell(j) + "~";
                    }
                }
                rowData += "\n";
                checkFirstPass++;
            }
        }
        Scanner traversal = new Scanner(rowData);
        traversal.useDelimiter("~");
        if (database.getCollection(fileName) == null) {
            database.createCollection(fileName, null);
        }
        DBCollection collection = database.getCollection(fileName);
        traversal.nextLine();
        while(traversal.hasNext()) {
            DBObject dataEntry = null;
            if(traversal.hasNext()) {
                String inp = traversal.next();
                if(!inp.isEmpty()) {
                    dataEntry = new BasicDBObject(attributes.get(0), inp);
                }
                for(int j = 1; j < attributes.size(); j++) {
                    if(traversal.hasNext()) {
                        dataEntry.put(attributes.get(j), traversal.next());
                    }
                }
            }
            if(!((BasicDBObject) dataEntry).isEmpty()) {
                if(!traversal.hasNextLine()) {
                    return;
                }
                collection.insert(dataEntry);
            }
        }
        return;
    }

    public static void assignWork(DB database) {
        if(database.getCollection("workerAssignments") == null) {
            database.createCollection("workerAssignments", null);
        }
        DBCollection collection = database.getCollection("workerAssignments");
        DBCollection workOrders = database.getCollection("Work Order Examples");
        DBCollection workDetails = database.getCollection("Worker Details");
        DBCursor workers = workDetails.find();
        LinkedList<String[]> workerData = new LinkedList<>();
        while(workers.hasNext()) {
            DBObject w = workers.next();
            String name = (String) w.get("Name");
            String equipmentCerts = (String) w.get("Equipment Certification(s)");
            String shift = (String) w.get("Shifts");
            String latitude = (String) w.get("Latitude");
            String longitude = (String) w.get("Longitude");
            String[] insert = {name, equipmentCerts, shift, latitude, longitude};
            workerData.add(insert);
        }
        DBCursor workOrder = workOrders.find();
        LinkedList<String[]> workOrderData = new LinkedList<>();
        while(workOrder.hasNext()) {
            DBObject o = workOrder.next();
            String orderNum = (String) o.get("Work Order #");
            String facility = (String) o.get("Facility");
            String equipmentType = (String) o.get("Equipment Type");
            String priority = (String) o.get("Priority(1-5)");
            String equipmentId = (String) o.get("Equipment ID");
            String time = (String) o.get("Time to Complete");
            String[] insert;
            if(workOrderData.isEmpty()) {
                insert = new String[]{"\n" + orderNum, facility, equipmentType, priority, equipmentId, time};
            }
            else {
                insert = new String[]{orderNum, facility, equipmentType, priority, equipmentId, time};
            }
            workOrderData.add(insert);
        }
        int[] arr = {0, 0, 0, 0, 0, 0};
        for(int i = 0; i < workOrderData.size(); i++) {
            arr[(int)Double.parseDouble(workOrderData.get(i)[3])]++;
        }
        LinkedList<String[]> prioritizedWorkOrder = new LinkedList<>();
        for(int i = 0; i < arr.length; i++) {
            if(arr[i] != 0) {
                for(int j = 0; j < workOrderData.size(); j++) {
                    String[] temp = workOrderData.get(j);
                    if((int)Double.parseDouble(temp[3]) == i) {
                        prioritizedWorkOrder.add(temp);
                    }
                }
                arr[i] = 0;
            }
        }
        LinkedList<String[]> workAssignments = new LinkedList<>();
        int jobsEachPerson = prioritizedWorkOrder.size()/workerData.size();
        int[] count = new int[prioritizedWorkOrder.size()];
        for(int i = 0; i < count.length; i++) {
            count[i] = 0;
        }
        for(int i = 0; i < workerData.size(); i++) {
            //assign jobs based on qualifications
            int j = 0;
            String temp = "";
            temp += workerData.get(i)[0] + "~ " + workerData.get(i)[1] + "~";
            for(int k = 0; k < prioritizedWorkOrder.size(); k++) {
                if((j < jobsEachPerson) && (workerData.get(i)[1].contains(prioritizedWorkOrder.get(k)[2])) && (count[k] == 0)) {
                    for(int e = 0; e < prioritizedWorkOrder.get(k).length; e++) {
                        temp += prioritizedWorkOrder.get(k)[e] + " ";
                    }
                    temp += "~";
                    j++;
                    count[k]++;
                }
            }
            if(!temp.isEmpty()) {
                workAssignments.add(temp.split("~"));
            }
        }
        for(int i = 0; i < count.length; i++) {
            if(count[i] == 0) {
                for(int j = 0; j < workAssignments.size(); j++) {
                    if(workAssignments.get(j)[1].contains(prioritizedWorkOrder.get(i)[2])) {
                        String temp = "";
                        for(int k = 0; k < workAssignments.get(j).length; k++) {
                            temp += workAssignments.get(j)[k] + "~";
                        }
                        for(int k = 0; k < prioritizedWorkOrder.get(i).length; k++) {
                            temp += prioritizedWorkOrder.get(i)[k] + " ";
                        }
                        workAssignments.set(j, temp.split("~"));
                        break;
                    }
                }
                count[i]++;
            }
        }
        for(int i = 0; i < workAssignments.size(); i++) {
            DBObject dataEntry = new BasicDBObject("Name", workAssignments.get(i)[0]);
            for(int j = 1; j < workAssignments.get(i).length; j++) {
                switch (j) {
                    case 1:
                        dataEntry.put("Equipment Certification(s)", workAssignments.get(i)[j]);
                        break;
                    default:
                        String wOrders = "";
                        while(j < workAssignments.get(i).length) {
                            wOrders += workAssignments.get(i)[j] + " ";
                            j++;
                        }
                        dataEntry.put("Work Order(s)", wOrders);
                        break;
                }
            }
            collection.insert(dataEntry);
        }
        for(int i = 0; i < workAssignments.size(); i++) {
            String message = "";
            for(int j = 0; j < workAssignments.get(i).length; j++) {
                switch (j) {
                    case 0:
                        if(i == 0) {
                            message += "Hey " + workAssignments.get(i)[j];
                        }
                        else {
                            message += "Hey " + workAssignments.get(i)[j].substring(1, workAssignments.get(i)[j].length());
                        }
                        break;
                    case 1:
                        message += ", based on your certifications" + workAssignments.get(i)[j] + ", you are able to fulfill these work orders.\n";
                        break;
                    default:
                        String wOrders = "";
                        while(j < workAssignments.get(i).length) {
                            String[] format = workAssignments.get(i)[j].split(" ");
                            for(int k = 0; k < format.length; k++) {
                                switch (k) {
                                    case 0:
                                        wOrders += "Work Order Number " + ((int) Double.parseDouble(format[k])) + ": ";
                                        break;
                                    case 1:
                                        wOrders += "At " + format[k] + ", ";
                                        break;
                                    case 2:
                                        wOrders += "fix the " + format[k] + ", ";
                                        break;
                                    case 4:
                                        wOrders += "more specifically equipment " + format[k] + ".";
                                        break;
                                    case 5:
                                        wOrders += " You should be able to finish this task by " + ((int) Double.parseDouble(format[k])) + " hours.";
                                    default:
                                        break;
                                }
                            }
                            wOrders += "\n";
                            j++;
                        }
                        message += wOrders;
                        break;
                }
            }
            //replace the first number with the number of the employee and using the twilio api it sends text messages at what they say is a competitive rate
            Message m = Message.creator(new com.twilio.type.PhoneNumber("+15124139229"), new com.twilio.type.PhoneNumber("+12162385541"), message).create();
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("RiceHackathonFile.xlsx");
        FileInputStream f = new FileInputStream(file);
        XSSFWorkbook workbook = new XSSFWorkbook(f);
        MongoClientURI uri = new MongoClientURI("mongodb+srv://Fiery-Silverbird:rk8AsLHC8n3fK0iV@silverbird.znujm.mongodb.net/silverbird?retryWrites=true&w=majority");
        MongoClient mongoClient = new MongoClient(uri);
        DB database = mongoClient.getDB("chevronDatabase");
        //adds the data from the xlsx file to a mongodb database, its already added to the database this code connects to, only run this loop if youre using a different database or want to add new items
        /*for(int i = 1; i < workbook.getNumberOfSheets(); i++) {
            convertXLSXtoCSVAndAddToDatabase(workbook.getSheetAt(i), workbook.getSheetAt(i).getSheetName(), database);
        }*/
        Twilio.init(auth_id, auth_tkn);
        assignWork(database);
    }
}