package com.hdsoft.gst;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Test extends Application {

    public static void main(String[] args) {
        launch(args);
    }
    public void start(Stage primary) throws Exception{
        GSTINValidator validator = new GSTINValidator();
        String[] s = {
                "18AYAPA2598P1ZZ",
                /*"18AUNPG67382ZN",
                "18AABFI4155K1Z9",
                "15ADZPH9868F2ZP"*/
        };

       /* validator.work((image,result)->{
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    JPanel panel = new JPanel(new BorderLayout(10, 10));
                    JLabel imageLabel = new JLabel(new ImageIcon(image));
                    JLabel instruction = new JLabel("Enter the 6-digit CAPTCHA:");
                    JTextField captchaField = new JTextField(10);
                    JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                    inputPanel.add(instruction);
                    inputPanel.add(captchaField);

                    panel.add(imageLabel, BorderLayout.CENTER);
                    panel.add(inputPanel, BorderLayout.SOUTH);

                    JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                    JDialog dialog = optionPane.createDialog(null, "GST CAPTCHA");

                    dialog.setModal(true);
                    dialog.setResizable(false);
                    dialog.setVisible(true);

                    Object selected = optionPane.getValue();
                    if (selected != null && selected.equals(JOptionPane.OK_OPTION)) {
                        result.set(captchaField.getText().trim());
                    }
                    dialog.dispose();
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                runnable.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(runnable);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });*/

        validator.work((image,result)->{
            WritableImage fxImage = SwingFXUtils.toFXImage(image, null);

            Stage stage = new Stage();
            stage.initOwner(primary);
            stage.centerOnScreen();
            stage.setResizable(false);
            stage.initStyle(StageStyle.UNDECORATED);


            VBox vb= new VBox();
            vb.setPadding(new Insets(35));
            vb.setSpacing(15);

            ImageView view = new ImageView(fxImage);
            view.setPreserveRatio(true);
            view.setFitWidth(320);

            TextField txt = new TextField();
            txt.setPadding(new Insets(7));
            txt.setPromptText("Enter Captcha");

            txt.setOnAction(e->{
                result.set(txt.getText().trim());
                stage.close();
            });

            vb.getChildren().addAll(view,txt);

            Scene sc = new Scene(vb);
            stage.setScene(sc);

            stage.showAndWait();
        });

        for(String gst : s){
            String res = validator.validateGSTIN(gst);
            System.out.println(res);
            System.out.println("********************************************************************");
            Thread.sleep(1000);
        }

        primary.show();
    }
}
