/*
 ***************************************************************
 *                                                             *
 *                           NOTICE                            *
 *                                                             *
 *   THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS             *
 *   CONFIDENTIAL INFORMATION OF INFOR AND/OR ITS AFFILIATES   *
 *   OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED WITHOUT PRIOR  *
 *   WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND       *
 *   ADAPT THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH  *
 *   THE TERMS OF THEIR SOFTWARE LICENSE AGREEMENT.            *
 *   ALL OTHER RIGHTS RESERVED.                                *
 *                                                             *
 *   (c) COPYRIGHT 2020 INFOR.  ALL RIGHTS RESERVED.           *
 *   THE WORD AND DESIGN MARKS SET FORTH HEREIN ARE            *
 *   TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR          *
 *   AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS        *
 *   RESERVED.  ALL OTHER TRADEMARKS LISTED HEREIN ARE         *
 *   THE PROPERTY OF THEIR RESPECTIVE OWNERS.                  *
 *                                                             *
 ***************************************************************
 */
 
 /*
 *Modification area - M3
 *Nbr               Date      User id          Description
 *SH001             20241001  ONKARK           Update header stop status in EXTOLN 
 *
 *
 */
 
public class UpdLineHdrCoStp extends ExtendM3Transaction {
    private final MIAPI mi;
    private final LoggerAPI logger; 
    private final DatabaseAPI database;
    private final MICallerAPI miCaller;

    /**
     * Constructor to initialize required APIs.
     *
     * @param mi       MIAPI instance
     * @param logger   LoggerAPI instance
     * @param database DatabaseAPI instance
     * @param miCaller MICallerAPI instance
     */
    public UpdLineHdrCoStp(MIAPI mi, LoggerAPI logger, DatabaseAPI database, MICallerAPI miCaller) {
        this.mi = mi;
        this.logger = logger;
        this.database = database; 
        this.miCaller = miCaller;
    }

    // Input variables
    private String inORNO, inCONO, inHSTP;
     private boolean isValidInput=true;

    public void main() {
        getApiInput();          
        if(validateInput()){
           updateHeaderStopInLine(); 
        }    
    }

    /**
     * Method to fetch input data from API.
     */
    public void getApiInput() {
        inCONO = mi.inData.get("CONO"); // Company code
        inORNO = mi.inData.get("ORNO").trim(); // Order number
        inHSTP = mi.inData.get("HSTP").trim(); // Header stop value
    }

    /**
     * Method to update the header stop in the EXTOLN table.
     */
    public void updateHeaderStopInLine() {
        DBAction action = database.table("EXTOLN").index("00").build();
        DBContainer container = action.createContainer();

        // Set parameters for the query
        container.set("EXCONO", inCONO.toInteger());
        container.set("EXORNO", inORNO);

        int nrOfKeys = 2; // Number of keys to lock

        // Read and lock the record, then update it
        action.readAllLock(container, nrOfKeys, updateCallBack);
    }

    /**
     * Method to validate input fields.
     */
    private boolean validateInput() {
        isValidInput = checkValidCompany();        // Validate company code
         if(isValidInput==false){
         return false;
        }
       isValidInput = checkValidOrderNumber();    // Validate order number
         if(isValidInput==false){
         return false;
        }
        return true;
    }

    /**
     * Callback for updating the EXTOLN table.
     */
    Closure<?> updateCallBack = { LockedResult lockedResult -> 
        lockedResult.set("EXHSTP", inHSTP.toInteger()); // Update the header stop value
        lockedResult.update(); // Commit the changes
    };

    /**
     * Validate company existence.
     */
    public boolean checkValidCompany() {
        // Check if company code contains only numeric values
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Invalid Company Code: '${inCONO}'. Company code must only contain numeric values.");
            return false;
        }

        DBAction query = database.table("CMNCMP").index("00").build();
        DBContainer container = query.getContainer();
        container.set("JICONO", inCONO.toInteger());

        // Check if the company exists in the database
        if (!query.read(container)) {
            mi.error("Company Code '${inCONO}' not found. Please verify the company code.");
            return false;
        }
        return true;
    }

    /**
     * Validate order number.
     */
    public boolean checkValidOrderNumber() {
        // Check if the order number length is at least 10 characters
        if (inORNO.toString().length() < 10) {
            mi.error("Invalid Order Number: '${inORNO}'. Order number must be at least 10 characters long.");
            return false;
        }

        DBAction query = database.table("OOHEAD").index("00").build();
        DBContainer container = query.getContainer();
        container.set("OACONO", inCONO.toInteger());
        container.set("OAORNO", inORNO);

        // Check if the order number exists in the database
        if (!query.read(container)) {
            mi.error("Order Number '${inORNO}' not found for Company Code '${inCONO}'. Please verify the order number.");
            return false;
        }
        return true;
    }
}
