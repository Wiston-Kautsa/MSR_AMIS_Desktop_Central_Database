package com.mycompany.msr.amis;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class AboutUsController {

    @FXML private VBox introContainer;
    @FXML private VBox purposeContainer;
    @FXML private VBox keyFunctionsContainer;
    @FXML private VBox whyItMattersContainer;
    @FXML private VBox developerTextContainer;
    @FXML private VBox summaryContainer;

    @FXML private Label introParagraphOne;
    @FXML private Label introParagraphTwo;
    @FXML private Label purposeParagraphOne;
    @FXML private Label purposeParagraphTwo;
    @FXML private Label purposeParagraphThree;
    @FXML private Label keyFunctionsParagraphOne;
    @FXML private Label keyFunctionsParagraphTwo;
    @FXML private Label keyFunctionsParagraphThree;
    @FXML private Label keyFunctionsParagraphFour;
    @FXML private Label keyFunctionsParagraphFive;
    @FXML private Label whyItMattersParagraphOne;
    @FXML private Label whyItMattersParagraphTwo;
    @FXML private Label whyItMattersParagraphThree;
    @FXML private Label whyItMattersParagraphFour;
    @FXML private Label developerParagraphOne;
    @FXML private Label developerParagraphTwo;
    @FXML private Label developerParagraphThree;
    @FXML private Label developerParagraphFour;
    @FXML private Label developerParagraphFive;
    @FXML private Label summaryParagraph;

    @FXML
    private void initialize() {
        configureParagraph(introParagraphOne, introContainer);
        configureParagraph(introParagraphTwo, introContainer);
        configureParagraph(purposeParagraphOne, purposeContainer);
        configureParagraph(purposeParagraphTwo, purposeContainer);
        configureParagraph(purposeParagraphThree, purposeContainer);
        configureParagraph(keyFunctionsParagraphOne, keyFunctionsContainer);
        configureParagraph(keyFunctionsParagraphTwo, keyFunctionsContainer);
        configureParagraph(keyFunctionsParagraphThree, keyFunctionsContainer);
        configureParagraph(keyFunctionsParagraphFour, keyFunctionsContainer);
        configureParagraph(keyFunctionsParagraphFive, keyFunctionsContainer);
        configureParagraph(whyItMattersParagraphOne, whyItMattersContainer);
        configureParagraph(whyItMattersParagraphTwo, whyItMattersContainer);
        configureParagraph(whyItMattersParagraphThree, whyItMattersContainer);
        configureParagraph(whyItMattersParagraphFour, whyItMattersContainer);
        configureParagraph(developerParagraphOne, developerTextContainer);
        configureParagraph(developerParagraphTwo, developerTextContainer);
        configureParagraph(developerParagraphThree, developerTextContainer);
        configureParagraph(developerParagraphFour, developerTextContainer);
        configureParagraph(developerParagraphFive, developerTextContainer);
        configureParagraph(summaryParagraph, summaryContainer);

        introParagraphOne.setText(
                "MSR AMIS, which stands for Asset Management Information System, " +
                "is an internal system developed to support asset management " +
                "activities within the Malawi Social Registry Unit. It helps the " +
                "unit record, organize, track, and manage ICT and other institutional " +
                "assets in a more efficient and reliable way."
        );
        introParagraphTwo.setText(
                "The system was introduced to improve accountability, strengthen " +
                "operational control, and make reporting easier. By keeping asset " +
                "information in one place, MSR AMIS supports better record management " +
                "and reduces the challenges that come with manual tracking."
        );

        purposeParagraphOne.setText(
                "MSR AMIS was developed to solve common problems in manual asset " +
                "tracking. In many cases, paper-based or scattered records make it " +
                "difficult to know the correct status of equipment at a given time."
        );
        purposeParagraphTwo.setText(
                "This can lead to missing records, delayed updates, weak " +
                "accountability, and difficulty preparing accurate summaries of " +
                "available, issued, or returned assets."
        );
        purposeParagraphThree.setText(
                "By digitizing these processes, MSR AMIS provides a centralized " +
                "and structured approach to asset control. It helps staff manage " +
                "assets more easily in daily operations and gives management a " +
                "clear and reliable view of current asset status."
        );

        keyFunctionsParagraphOne.setText(
                "The system records assets and stores key details such as asset " +
                "code, category, condition, and status."
        );
        keyFunctionsParagraphTwo.setText(
                "It tracks asset assignment and distribution. This helps users know " +
                "which equipment is issued, borrowed, returned, or available."
        );
        keyFunctionsParagraphThree.setText(
                "MSR AMIS also supports follow-up on assets that are still in use " +
                "or awaiting return."
        );
        keyFunctionsParagraphFour.setText(
                "The system generates reports that improve accountability, stock " +
                "visibility, and decision-making."
        );
        keyFunctionsParagraphFive.setText(
                "It also improves record management by reducing duplication, " +
                "lowering loss risk, and minimizing manual errors."
        );

        whyItMattersParagraphOne.setText(
                "MSR AMIS matters because good asset management is essential for " +
                "accountability and proper use of institutional resources. When " +
                "asset records are incomplete or hard to track, equipment can " +
                "easily be misplaced, duplicated, or left without proper follow-up."
        );
        whyItMattersParagraphTwo.setText(
                "The system improves this situation by giving staff and management " +
                "a clear and up-to-date view of what equipment is available, what " +
                "has been assigned, what has been returned, and what still needs attention."
        );
        whyItMattersParagraphThree.setText(
                "This makes daily work easier, reduces confusion when checking " +
                "asset status, and supports faster decision-making. It also reduces " +
                "dependence on manual files and scattered records, strengthens " +
                "accountability for issued assets, and makes reporting more reliable."
        );
        whyItMattersParagraphFour.setText(
                "In practical terms, MSR AMIS helps the unit work more efficiently " +
                "while maintaining better control over its assets."
        );

        developerParagraphOne.setText(
                "MSR AMIS was coded and developed by Wiston Kautsa, a Data " +
                "Management Assistant in the Malawi Social Registry Unit."
        );
        developerParagraphTwo.setText(
                "The system was developed in response to practical needs observed " +
                "in day-to-day asset management."
        );
        developerParagraphThree.setText(
                "Its design was guided by the need to improve asset tracking, " +
                "strengthen accountability, and increase the accuracy of records."
        );
        developerParagraphFour.setText(
                "It reflects direct understanding of the unit's workflow, including " +
                "asset registration, assignment, monitoring, reporting, and follow-up " +
                "on issued equipment."
        );
        developerParagraphFive.setText(
                "This makes the system practical and relevant to the real working " +
                "environment in which it is used."
        );

        summaryParagraph.setText(
                "MSR AMIS is a practical asset management system that helps the " +
                "Malawi Social Registry Unit record, track, issue, monitor, and " +
                "report on institutional assets more effectively."
        );
    }

    private void configureParagraph(Label label, Region container) {
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.prefWidthProperty().bind(container.widthProperty().subtract(4));
    }
}
