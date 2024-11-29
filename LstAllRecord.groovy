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
 *SH001            20241001   ONKARK           script retrieves all records from EXTOLN table
 *
 *
 */

/****************************************************************************************
 Extension Name: EXT100MI/LstAllRecord
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date: 
 Description:
 * This script retrieves all records from the EXTOLN table. It reads up to 10,000 
 * records at a time and outputs relevant details to the API.

 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created
******************************************************************************************/
public class LstAllRecord extends ExtendM3Transaction {
    private final MIAPI mi;
    private final LoggerAPI logger;
    private final DatabaseAPI database;


    /*
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param database - Infor Database Interface
     */
    public LstAllRecord(MIAPI mi, LoggerAPI logger, DatabaseAPI database) {
        this.mi = mi;
        this.logger = logger;
        this.database = database;
    }

    public void main() {
        getAllRecords(); 
    }


    /*
     * Retrieves all records from the EXTOLN table and processes them.
     */
    private void getAllRecords() {
        DBAction action = database.table("EXTOLN").index("00").selectAllFields().build();
        DBContainer container = action.createContainer();

        // Read and process records from EXTOLN table
        // int nrOfRecords = 10000; // Set a maximum of 10,000 records
         int nrOfRecords = mi.getMaxRecords() <= 0 || mi.getMaxRecords() >= 10000? 10000: mi.getMaxRecords();    
        action.readAll(container, 0, nrOfRecords, listRecords);
    }


    /*
     * Processes each record and outputs relevant fields to the API.
     */
    Closure<?> listRecords = { DBContainer container -> 
        mi.outData.put("CONO", container.get("EXCONO").toString().trim());
        mi.outData.put("ORNO", container.get("EXORNO").toString().trim());
        mi.outData.put("PONR", container.get("EXPONR").toString().trim());
        mi.outData.put("POSX", container.get("EXPOSX").toString().trim());
        mi.outData.put("WSTA", container.get("EXWSTA").toString().trim());
        mi.outData.put("STOP", container.get("EXSTOP").toString().trim());
        mi.outData.put("TSID", container.get("EXTSID").toString().trim());
        mi.outData.put("HSTP", container.get("EXHSTP").toString().trim());
        mi.outData.put("LMDT", container.get("EXLMDT").toString().trim());
        mi.outData.put("RGDT", container.get("EXRGDT").toString().trim());
        mi.outData.put("RGTM", container.get("EXRGTM").toString().trim());
        mi.outData.put("CHNO", container.get("EXCHNO").toString().trim());
        mi.outData.put("CHID", container.get("EXCHID").toString().trim());
        mi.write(); // Output the result
    }
}